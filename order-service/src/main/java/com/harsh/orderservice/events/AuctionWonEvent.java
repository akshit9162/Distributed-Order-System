package com.harsh.orderservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published by auction-service when an auction ends with a winner. order-service turns it into an order. */
public record AuctionWonEvent(UUID auctionId, String winnerId, String sellerId, String productId,
                              BigDecimal price, Instant endedAt) {
}
