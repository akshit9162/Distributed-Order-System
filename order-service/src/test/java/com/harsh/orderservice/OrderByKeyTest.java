package com.harsh.orderservice;

import com.harsh.orderservice.controller.OrderController;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.repository.OrderRepository;
import com.harsh.orderservice.service.OrderCreationService;
import com.harsh.orderservice.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GET /orders/by-key/{key}: how the auction page finds the winner's order. */
class OrderByKeyTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final MockMvc http = MockMvcBuilders.standaloneSetup(
            new OrderController(mock(OrderCreationService.class), new OrderQueryService(repository))).build();

    @Test
    void finds_the_order_created_for_an_auction() throws Exception {
        when(repository.findByIdempotencyKey("auction-42"))
                .thenReturn(Optional.of(new Order("auction-42", "alice", "camera-1", 1, new BigDecimal("41.00"))));

        http.perform(get("/orders/by-key/auction-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("alice"));
    }

    @Test
    void unknown_key_is_404() throws Exception {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        http.perform(get("/orders/by-key/auction-nope")).andExpect(status().isNotFound());
    }
}
