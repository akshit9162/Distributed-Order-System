package com.harsh.orderservice.service;

import com.harsh.orderservice.events.OrderCreatedEvent;
import com.harsh.orderservice.kafka.OrderEventProducer;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The one way orders get created, used by both the HTTP API and the auction-won listener, so every order (bought
 * directly or won at auction) gets the same idempotency guarantee and enters the same SAGA.
 */
@Service
public class OrderCreationService {

    public record Result(Order order, boolean created) {
    }

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    public OrderCreationService(OrderRepository orderRepository, OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
    }

    public Result create(String idempotencyKey, String customerId, String productId, int quantity, BigDecimal amount) {
        // If this key was seen before (a client retry, or a redelivered Kafka event), return the existing order
        // instead of creating a second one.
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new Result(existing.get(), false);
        }

        Order order;
        try {
            order = orderRepository.save(new Order(idempotencyKey, customerId, productId, quantity, amount));
        } catch (DataIntegrityViolationException e) {
            // Race: two requests with the same key passed the check above at the same instant. The unique
            // constraint on idempotency_key is the real source of truth; return whichever one won.
            Order winner = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return new Result(winner, false);
        }

        orderEventProducer.publishOrderCreated(new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity(), order.getAmount()));
        return new Result(order, true);
    }
}
