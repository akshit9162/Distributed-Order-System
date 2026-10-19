package com.harsh.orderservice;

import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.model.OrderStatus;
import com.harsh.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two saga events for the same order processed at the same time: each loads the order, changes it, saves it.
 * The second save must fail (and be retried by Kafka) instead of overwriting the first.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // each repository call commits on its own, like two listeners
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class OrderConcurrentUpdateTest {

    @Autowired
    private OrderRepository orders;

    @Test
    void slower_writer_cannot_overwrite_a_newer_status() {
        var id = orders.save(new Order("k-race", "alice", "camera-1", 1, new BigDecimal("10.00"))).getId();

        Order paymentThreadCopy = orders.findById(id).orElseThrow();     // both read PENDING
        Order inventoryThreadCopy = orders.findById(id).orElseThrow();

        paymentThreadCopy.transitionTo(OrderStatus.COMPLETED);
        orders.save(paymentThreadCopy);                                   // first commit wins

        inventoryThreadCopy.transitionTo(OrderStatus.INVENTORY_RESERVED);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> orders.save(inventoryThreadCopy));

        assertEquals(OrderStatus.COMPLETED, orders.findById(id).orElseThrow().getStatus());
    }
}
