package com.harsh.orderservice.kafka;

import com.harsh.orderservice.config.KafkaTopicConfig;
import com.harsh.orderservice.events.AuctionWonEvent;
import com.harsh.orderservice.service.OrderCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns a won auction into an order, which then runs the normal SAGA (reserve inventory, take payment, roll back
 * on failure). auction-service delivers AuctionWonEvent at least once, so the order's idempotency key is derived
 * from the auction id: a redelivered event finds the existing order and does nothing.
 */
@Component
public class AuctionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventConsumer.class);

    private final OrderCreationService orderCreationService;

    public AuctionEventConsumer(OrderCreationService orderCreationService) {
        this.orderCreationService = orderCreationService;
    }

    public static String idempotencyKeyFor(AuctionWonEvent event) {
        return "auction-" + event.auctionId();
    }

    @KafkaListener(topics = KafkaTopicConfig.AUCTION_WON_TOPIC, groupId = "order-service-group", containerFactory = "auctionWonListenerFactory")
    public void onAuctionWon(AuctionWonEvent event) {
        var result = orderCreationService.create(idempotencyKeyFor(event), event.winnerId(), event.productId(), 1, event.price());
        log.info("Auction {} won by {} at {}: order {} {}", event.auctionId(), event.winnerId(), event.price(),
                result.order().getId(), result.created() ? "created" : "already existed (redelivery)");
    }
}
