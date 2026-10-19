package com.akshit.auctionservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by auction-service when an auction ends with a winner. Consumed by order-service, which turns it into a
 * normal order (and so into the inventory -> payment SAGA). auctionId doubles as the idempotency key downstream.
 */
public record AuctionWonEvent(UUID auctionId, String winnerId, String sellerId, String productId,
                              BigDecimal price, Instant endedAt) {
}
