package com.harsh.orderservice.controller;

import com.harsh.orderservice.dto.CreateOrderRequest;
import com.harsh.orderservice.dto.OrderResponse;
import com.harsh.orderservice.service.OrderCreationService;
import com.harsh.orderservice.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderCreationService orderCreationService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderCreationService orderCreationService, OrderQueryService orderQueryService) {
        this.orderCreationService = orderCreationService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // Idempotency and the duplicate-key race are handled in OrderCreationService, shared with the
        // auction-won listener. A repeated key returns the existing order with 200 instead of 201.
        var result = orderCreationService.create(request.idempotencyKey(), request.customerId(),
                request.productId(), request.quantity(), request.amount());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result.order()))
                : ResponseEntity.ok(OrderResponse.from(result.order()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        // Cache-aside: OrderQueryService checks Redis first, falls back to
        // Postgres on a miss. See CacheConfig for the eviction strategy.
        return ResponseEntity.ok(orderQueryService.getOrder(id));
    }

    /** Find an order by the key it was created with (auction-service uses "auction-{auctionId}"). */
    @GetMapping("/by-key/{idempotencyKey}")
    public ResponseEntity<OrderResponse> getOrderByKey(@PathVariable String idempotencyKey) {
        return orderQueryService.findByIdempotencyKey(idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
