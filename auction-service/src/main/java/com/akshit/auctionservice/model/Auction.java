package com.akshit.auctionservice.model;

import com.akshit.auctionservice.domain.ProxyBidding;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One auction. Concurrent bids on the same auction are serialised by optimistic locking: every bid reads the row,
 * applies the proxy-bidding rules, and writes it back; if another bid committed in between, the @Version check
 * fails and the bid is retried on fresh data (see BiddingService). No locks are held while a bidder waits.
 */
@Entity
@Table(name = "auctions", indexes = @Index(name = "ix_auctions_status_ends", columnList = "status, ends_at"))
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal startPrice;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "leader_id")
    private String leaderId;

    /** The leader's secret maximum. Never returned by the API (only the current price is public). */
    @Column(name = "leader_max", precision = 12, scale = 2)
    private BigDecimal leaderMax;

    @Column(name = "bid_count", nullable = false)
    private int bidCount;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    /** Outbox flag: true once AuctionWonEvent has been acknowledged by Kafka. */
    @Column(name = "won_event_published", nullable = false)
    private boolean wonEventPublished;

    protected Auction() {
        // JPA
    }

    public Auction(String sellerId, String productId, String title, BigDecimal startPrice, Instant endsAt) {
        this.sellerId = sellerId;
        this.productId = productId;
        this.title = title;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.endsAt = endsAt;
        this.status = AuctionStatus.OPEN;
    }

    public ProxyBidding.State biddingState() {
        return new ProxyBidding.State(startPrice, currentPrice, leaderId, leaderMax);
    }

    public void apply(ProxyBidding.State next) {
        this.currentPrice = next.currentPrice();
        this.leaderId = next.leaderId();
        this.leaderMax = next.leaderMax();
        this.bidCount++;
    }

    public boolean isOpenAt(Instant now) {
        return status == AuctionStatus.OPEN && now.isBefore(endsAt);
    }

    public void close() {
        if (status != AuctionStatus.OPEN) return;
        status = leaderId == null ? AuctionStatus.UNSOLD : AuctionStatus.SOLD;
    }

    public void markWonEventPublished() {
        wonEventPublished = true;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getSellerId() { return sellerId; }
    public String getProductId() { return productId; }
    public String getTitle() { return title; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public String getLeaderId() { return leaderId; }
    public BigDecimal getLeaderMax() { return leaderMax; }
    public int getBidCount() { return bidCount; }
    public Instant getEndsAt() { return endsAt; }
    public AuctionStatus getStatus() { return status; }
    public boolean isWonEventPublished() { return wonEventPublished; }
}
