package com.akshit.auctionservice.model;

import com.akshit.auctionservice.domain.ProxyBidding;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An accepted bid. "sequence" is the auction's version right after this bid was applied, so ordering by it gives
 * the exact order bids were applied in, which lets a test replay them and check the result.
 * The idempotency key is unique per auction, so a client retry can never place the same bid twice.
 */
@Entity
@Table(name = "bids",
        uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "idempotency_key"}),
        indexes = @Index(name = "ix_bids_auction_seq", columnList = "auction_id, sequence"))
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(name = "max_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxAmount;

    /** The auction's public price right after this bid. */
    @Column(name = "price_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProxyBidding.Outcome outcome;

    @Column(nullable = false)
    private long sequence;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected Bid() {
        // JPA
    }

    public Bid(UUID auctionId, String idempotencyKey, String bidderId, BigDecimal maxAmount,
               BigDecimal priceAfter, ProxyBidding.Outcome outcome, long sequence, Instant placedAt) {
        this.auctionId = auctionId;
        this.idempotencyKey = idempotencyKey;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.priceAfter = priceAfter;
        this.outcome = outcome;
        this.sequence = sequence;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public UUID getAuctionId() { return auctionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getBidderId() { return bidderId; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public BigDecimal getPriceAfter() { return priceAfter; }
    public ProxyBidding.Outcome getOutcome() { return outcome; }
    public long getSequence() { return sequence; }
    public Instant getPlacedAt() { return placedAt; }
}
