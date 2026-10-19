package com.akshit.auctionservice.service;

import com.akshit.auctionservice.domain.ProxyBidding;
import com.akshit.auctionservice.dto.Dtos.BidResult;
import com.akshit.auctionservice.dto.Dtos.PlaceBidRequest;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.Bid;
import com.akshit.auctionservice.repository.AuctionRepository;
import com.akshit.auctionservice.repository.BidRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Places bids with optimistic concurrency control.
 *
 * Each attempt is its own short transaction: read the auction, apply the proxy-bidding rules, write the auction
 * back (the @Version check happens here) and record the bid. If another bid committed first, the write fails with
 * an optimistic-lock conflict; we back off for a random few milliseconds and retry on the fresh state. Popular
 * auctions get many conflicts near the end, which is exactly the case this is built for.
 *
 * Why optimistic and not a row lock (SELECT ... FOR UPDATE)? A bid is a few microseconds of work, so conflicts
 * are cheap to retry, nobody holds a lock while a slow client or network hiccup stalls, and the same code works
 * on any database. Inventory in this system uses a Redis lock instead because it guards a resource shared across
 * many orders; here each auction is its own unit of contention.
 */
@Service
public class BiddingService {

    public static final int MAX_ATTEMPTS = 100;

    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final TransactionTemplate tx;
    private final AuctionUpdates updates;
    private final Clock clock;
    private final AtomicLong conflicts = new AtomicLong();

    public BiddingService(AuctionRepository auctions, BidRepository bids, TransactionTemplate tx,
                          AuctionUpdates updates, Clock clock) {
        this.auctions = auctions;
        this.bids = bids;
        this.tx = tx;
        this.updates = updates;
        this.clock = clock;
    }

    /** Optimistic-lock conflicts seen so far (each one was retried). */
    public long conflicts() {
        return conflicts.get();
    }

    public BidResult placeBid(UUID auctionId, PlaceBidRequest request) {
        var normalized = new PlaceBidRequest(request.idempotencyKey(), request.bidderId(),
                request.maxAmount().setScale(2, RoundingMode.UNNECESSARY));

        var retry = bids.findByAuctionIdAndIdempotencyKey(auctionId, normalized.idempotencyKey());
        if (retry.isPresent()) return duplicateOf(retry.get());

        for (int attempt = 1; ; attempt++) {
            try {
                BidResult result = tx.execute(status -> attempt(auctionId, normalized));
                if (result.accepted()) updates.publish(auctionId);   // after commit
                return result;
            } catch (ObjectOptimisticLockingFailureException e) {
                conflicts.incrementAndGet();
                if (attempt >= MAX_ATTEMPTS) throw e;
                backOff(attempt);
            } catch (DataIntegrityViolationException e) {
                // Two copies of the same retried bid raced; the unique key let exactly one in.
                return bids.findByAuctionIdAndIdempotencyKey(auctionId, normalized.idempotencyKey()).map(this::duplicateOf).orElseThrow(() -> e);
            }
        }
    }

    private BidResult attempt(UUID auctionId, PlaceBidRequest req) {
        Auction auction = auctions.findById(auctionId).orElseThrow(() -> new AuctionNotFoundException(auctionId));
        Instant now = clock.instant();
        if (!auction.isOpenAt(now)) return BidResult.closed(auction);
        if (auction.getSellerId().equals(req.bidderId())) return BidResult.ownAuction(auction);

        ProxyBidding.Result result = ProxyBidding.apply(auction.biddingState(), req.bidderId(), req.maxAmount());
        if (!result.accepted()) return BidResult.from(result.outcome(), auction, req.bidderId(), false);

        auction.apply(result.state());
        auctions.saveAndFlush(auction);   // version check: throws if another bid got here first
        bids.save(new Bid(auction.getId(), req.idempotencyKey(), req.bidderId(), req.maxAmount(),
                auction.getCurrentPrice(), result.outcome(), auction.getVersion(), now));
        return BidResult.from(result.outcome(), auction, req.bidderId(), false);
    }

    private BidResult duplicateOf(Bid bid) {
        Auction auction = auctions.findById(bid.getAuctionId()).orElseThrow();
        return BidResult.from(bid.getOutcome(), auction, bid.getBidderId(), true);
    }

    private static void backOff(int attempt) {
        try {
            // Random jitter so the losers of a race don't all retry at the same instant and collide again.
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 2 + Math.min(attempt, 20)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
