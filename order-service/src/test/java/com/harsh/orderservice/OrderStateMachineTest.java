package com.harsh.orderservice;

import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Events from different Kafka topics can arrive in any order. Found in an end-to-end run: payment-confirmed was
 * processed before inventory-reserved, the payment event was rejected as "out of order" and dropped, and the
 * order stayed in INVENTORY_RESERVED forever.
 */
class OrderStateMachineTest {

    private Order order() {
        return new Order("k", "alice", "camera-1", 1, new BigDecimal("10.00"));
    }

    @Test
    void normal_order_of_events_completes() {
        Order o = order();
        o.transitionTo(OrderStatus.INVENTORY_RESERVED);
        o.transitionTo(OrderStatus.COMPLETED);
        assertEquals(OrderStatus.COMPLETED, o.getStatus());
    }

    @Test
    void payment_confirmed_arriving_before_inventory_reserved_still_completes() {
        Order o = order();
        o.transitionTo(OrderStatus.COMPLETED);   // payment result first
        assertEquals(OrderStatus.COMPLETED, o.getStatus());

        // the late InventoryReservedEvent is stale: rejected, and the consumer ignores it
        assertThrows(IllegalStateException.class, () -> o.transitionTo(OrderStatus.INVENTORY_RESERVED));
        assertEquals(OrderStatus.COMPLETED, o.getStatus());
    }

    @Test
    void payment_rejected_arriving_first_still_compensates() {
        Order o = order();
        o.transitionTo(OrderStatus.ROLLING_BACK);
        o.transitionTo(OrderStatus.ROLLED_BACK);
        assertEquals(OrderStatus.ROLLED_BACK, o.getStatus());
        assertThrows(IllegalStateException.class, () -> o.transitionTo(OrderStatus.INVENTORY_RESERVED));
    }

    @Test
    void finished_orders_cannot_move_again() {
        Order o = order();
        o.transitionTo(OrderStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> o.transitionTo(OrderStatus.ROLLING_BACK));
    }

    @Test
    void redelivered_event_for_the_current_state_is_a_no_op() {
        Order o = order();
        o.transitionTo(OrderStatus.INVENTORY_RESERVED);
        assertDoesNotThrow(() -> o.transitionTo(OrderStatus.INVENTORY_RESERVED));
    }
}
