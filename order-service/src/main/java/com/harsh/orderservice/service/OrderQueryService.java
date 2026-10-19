package com.harsh.orderservice.service;

import com.harsh.orderservice.dto.OrderResponse;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.repository.OrderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderQueryService {

    public static final String ORDERS_CACHE = "orders";

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Cacheable(value = ORDERS_CACHE, key = "#id")
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
        return OrderResponse.from(order);
    }

    /**
     * Look an order up by its idempotency key, e.g. "auction-{auctionId}" for an auction win. Not cached: the
     * auction page polls this to show the SAGA progressing, so it must always see the latest status.
     */
    public Optional<OrderResponse> findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey).map(OrderResponse::from);
    }
}
