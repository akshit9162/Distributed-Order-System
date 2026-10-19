package com.harsh.orderservice;

import com.harsh.orderservice.events.AuctionWonEvent;
import com.harsh.orderservice.events.OrderCreatedEvent;
import com.harsh.orderservice.kafka.AuctionEventConsumer;
import com.harsh.orderservice.kafka.OrderEventProducer;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.model.OrderStatus;
import com.harsh.orderservice.repository.OrderRepository;
import com.harsh.orderservice.service.OrderCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for the auction -> order hand-off (no broker or database needed). */
class AuctionOrderTest {

    private OrderRepository repository;
    private OrderEventProducer producer;
    private AuctionEventConsumer consumer;

    private final AuctionWonEvent event = new AuctionWonEvent(UUID.randomUUID(), "alice", "seller-1", "camera-1",
            new BigDecimal("41.00"), Instant.now());

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        producer = mock(OrderEventProducer.class);
        consumer = new AuctionEventConsumer(new OrderCreationService(repository, producer));
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void won_auction_becomes_a_pending_order_for_the_winner_at_the_winning_price() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        consumer.onAuctionWon(event);

        var saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(saved.capture());
        Order order = saved.getValue();
        assertEquals("auction-" + event.auctionId(), order.getIdempotencyKey());
        assertEquals("alice", order.getCustomerId());
        assertEquals("camera-1", order.getProductId());
        assertEquals(1, order.getQuantity());
        assertEquals(0, new BigDecimal("41.00").compareTo(order.getAmount()));
        assertEquals(OrderStatus.PENDING, order.getStatus());

        var published = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(producer).publishOrderCreated(published.capture());   // enters the SAGA
        assertEquals("alice", published.getValue().customerId());
    }

    @Test
    void redelivered_event_does_not_create_a_second_order() {
        Order existing = new Order("auction-" + event.auctionId(), "alice", "camera-1", 1, new BigDecimal("41.00"));
        when(repository.findByIdempotencyKey("auction-" + event.auctionId())).thenReturn(Optional.of(existing));

        consumer.onAuctionWon(event);

        verify(repository, never()).save(any());
        verify(producer, never()).publishOrderCreated(any());
    }

    @Test
    void two_deliveries_racing_on_the_unique_key_produce_one_order() {
        Order winner = new Order("auction-" + event.auctionId(), "alice", "camera-1", 1, new BigDecimal("41.00"));
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var result = new OrderCreationService(repository, producer)
                .create("auction-" + event.auctionId(), "alice", "camera-1", 1, new BigDecimal("41.00"));

        assertFalse(result.created());
        assertSame(winner, result.order());
        verify(producer, never()).publishOrderCreated(any());
    }
}
