package com.akshit.auctionservice.dto;

import com.akshit.auctionservice.domain.ProxyBidding;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.AuctionStatus;
import com.akshit.auctionservice.model.Bid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    public record CreateAuctionRequest(
            @NotBlank @Size(max = 64) String sellerId,
            @NotBlank @Size(max = 64) String productId,
            @NotBlank @Size(max = 120) String title,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal startPrice,
            @Min(1) @Max(7 * 24 * 3600) long durationSeconds) {
    }

    public record PlaceBidRequest(
            @NotBlank @Size(max = 80) String idempotencyKey,
            @NotBlank @Size(max = 64) String bidderId,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal maxAmount) {
    }

    /** Public view of an auction. The leader's maximum is deliberately not included. */
    public record AuctionView(UUID id, String title, String productId, String sellerId, BigDecimal startPrice,
                              BigDecimal currentPrice, BigDecimal minimumNextBid, String leaderId, int bidCount,
                              Instant endsAt, AuctionStatus status) {
        public static AuctionView of(Auction a) {
            return new AuctionView(a.getId(), a.getTitle(), a.getProductId(), a.getSellerId(), a.getStartPrice(),
                    a.getCurrentPrice(), a.biddingState().minimumBid(), a.getLeaderId(), a.getBidCount(),
                    a.getEndsAt(), a.getStatus());
        }
    }

    public enum BidOutcome { LEADING, MAX_RAISED, OUTBID, TOO_LOW, AUCTION_CLOSED, OWN_AUCTION }

    public record BidResult(BidOutcome outcome, boolean leading, BigDecimal currentPrice, BigDecimal minimumNextBid,
                            int bidCount, boolean duplicate) {

        public static BidResult from(ProxyBidding.Outcome o, Auction a, String bidderId, boolean duplicate) {
            return new BidResult(BidOutcome.valueOf(o.name()), bidderId.equals(a.getLeaderId()), a.getCurrentPrice(),
                    a.biddingState().minimumBid(), a.getBidCount(), duplicate);
        }

        /** Sellers may not bid on their own items (shill bidding would push the price up artificially). */
        public static BidResult ownAuction(Auction a) {
            return new BidResult(BidOutcome.OWN_AUCTION, false, a.getCurrentPrice(), a.biddingState().minimumBid(),
                    a.getBidCount(), false);
        }

        public static BidResult closed(Auction a) {
            return new BidResult(BidOutcome.AUCTION_CLOSED, false, a.getCurrentPrice(), null, a.getBidCount(), false);
        }

        public boolean accepted() {
            return outcome == BidOutcome.LEADING || outcome == BidOutcome.MAX_RAISED || outcome == BidOutcome.OUTBID;
        }
    }

    public record BidView(String bidderId, BigDecimal priceAfter, ProxyBidding.Outcome outcome, long sequence,
                          Instant placedAt) {
        public static BidView of(Bid b) {
            return new BidView(b.getBidderId(), b.getPriceAfter(), b.getOutcome(), b.getSequence(), b.getPlacedAt());
        }
    }
}
