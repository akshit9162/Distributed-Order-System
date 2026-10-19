package com.akshit.auctionservice.domain;

import java.math.BigDecimal;

/**
 * Second-price proxy bidding, as a pure function: (auction state, new maximum bid) -> (new state, outcome).
 *
 * A bidder enters the most they are willing to pay. The system bids on their behalf, only as much as needed:
 * the leader pays one increment above the second-highest maximum (never more than their own maximum).
 *
 * Rules:
 *  1. The first bid must be at least the start price; the price stays at the start price.
 *  2. The leader may raise their own maximum; the visible price does not move (you never bid against yourself).
 *  3. Anyone else must bid at least price + increment.
 *  4. A higher maximum takes the lead at min(their max, old leader's max + increment).
 *  5. An equal or lower maximum is accepted but immediately outbid: the price rises to min(leader max, their max
 *     + increment). On a tie, the earlier bid keeps the lead.
 *
 * Kept free of Spring and JPA so the rules can be unit-tested and replayed exactly.
 */
public final class ProxyBidding {

    public enum Outcome { LEADING, MAX_RAISED, OUTBID, TOO_LOW }

    /** Snapshot of the parts of an auction the rules need. leaderId == null means no bids yet. */
    public record State(BigDecimal startPrice, BigDecimal currentPrice, String leaderId, BigDecimal leaderMax) {
        public static State opening(BigDecimal startPrice) {
            return new State(startPrice, startPrice, null, null);
        }

        /** The lowest maximum a non-leader can bid right now. */
        public BigDecimal minimumBid() {
            return leaderId == null ? startPrice : currentPrice.add(BidIncrements.at(currentPrice));
        }
    }

    public record Result(State state, Outcome outcome) {
        public boolean accepted() {
            return outcome != Outcome.TOO_LOW;
        }
    }

    private ProxyBidding() {
    }

    public static Result apply(State s, String bidderId, BigDecimal max) {
        if (s.leaderId() == null) {
            if (max.compareTo(s.startPrice()) < 0) return new Result(s, Outcome.TOO_LOW);
            return new Result(new State(s.startPrice(), s.startPrice(), bidderId, max), Outcome.LEADING);
        }

        if (bidderId.equals(s.leaderId())) {
            if (max.compareTo(s.leaderMax()) <= 0) return new Result(s, Outcome.TOO_LOW);
            return new Result(new State(s.startPrice(), s.currentPrice(), bidderId, max), Outcome.MAX_RAISED);
        }

        if (max.compareTo(s.minimumBid()) < 0) return new Result(s, Outcome.TOO_LOW);

        if (max.compareTo(s.leaderMax()) > 0) {
            BigDecimal price = min(max, s.leaderMax().add(BidIncrements.at(s.leaderMax())));
            return new Result(new State(s.startPrice(), price, bidderId, max), Outcome.LEADING);
        }

        // max <= leader's max: the leader's proxy answers, up to their own maximum (ties go to the earlier bid).
        BigDecimal price = min(s.leaderMax(), max.add(BidIncrements.at(max)));
        return new Result(new State(s.startPrice(), price, s.leaderId(), s.leaderMax()), Outcome.OUTBID);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
