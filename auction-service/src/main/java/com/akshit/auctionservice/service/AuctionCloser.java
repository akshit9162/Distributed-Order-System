package com.akshit.auctionservice.service;

import com.akshit.auctionservice.events.AuctionWonEvent;
import com.akshit.auctionservice.kafka.AuctionEventProducer;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.AuctionStatus;
import com.akshit.auctionservice.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Ends auctions on time and hands winners to the order SAGA.
 *
 * Closing: a scheduler finds OPEN auctions past their end time and marks them SOLD or UNSOLD. It writes through
 * the same @Version check as bidding, so a bid that was placed just before the deadline and commits a moment
 * later is never lost: whichever commits second retries and sees the other's change. Bids at or after the end
 * time are rejected by BiddingService.
 *
 * Hand-off (a small transactional outbox): the SOLD status and a "won event not yet published" flag are committed
 * together. Publishing to Kafka happens afterwards, and the flag is only set once Kafka acknowledges. If the
 * process dies in between, the next run publishes it again. That makes delivery at-least-once, so order-service
 * uses the auction id as the order's idempotency key and a repeat never creates a second order.
 */
@Component
public class AuctionCloser {

    private static final Logger log = LoggerFactory.getLogger(AuctionCloser.class);

    private final AuctionRepository auctions;
    private final AuctionEventProducer producer;
    private final AuctionUpdates updates;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AuctionCloser(AuctionRepository auctions, AuctionEventProducer producer, AuctionUpdates updates,
                         TransactionTemplate tx, Clock clock) {
        this.auctions = auctions;
        this.producer = producer;
        this.updates = updates;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${auction.close-interval-ms:1000}")
    public void run() {
        closeDueAuctions();
        publishPendingWins();
    }

    public int closeDueAuctions() {
        int closed = 0;
        for (Auction due : auctions.findByStatusAndEndsAtLessThanEqual(AuctionStatus.OPEN, clock.instant())) {
            if (closeOne(due.getId())) closed++;
        }
        return closed;
    }

    private boolean closeOne(UUID id) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                Boolean changed = tx.execute(s -> {
                    Auction a = auctions.findById(id).orElseThrow();
                    if (a.getStatus() != AuctionStatus.OPEN || clock.instant().isBefore(a.getEndsAt())) return false;
                    a.close();
                    auctions.saveAndFlush(a);
                    return true;
                });
                if (Boolean.TRUE.equals(changed)) updates.publish(id);
                return Boolean.TRUE.equals(changed);
            } catch (ObjectOptimisticLockingFailureException e) {
                // A last-moment bid committed first; reload and close with it included.
            }
        }
        return false;
    }

    public int publishPendingWins() {
        int published = 0;
        for (Auction a : auctions.findByStatusAndWonEventPublishedFalse(AuctionStatus.SOLD)) {
            try {
                producer.publishWon(new AuctionWonEvent(a.getId(), a.getLeaderId(), a.getSellerId(), a.getProductId(),
                        a.getCurrentPrice(), a.getEndsAt()));
            } catch (Exception e) {
                log.warn("Could not publish AuctionWonEvent for {} yet, will retry: {}", a.getId(), e.getMessage());
                continue;
            }
            markPublished(a.getId());
            published++;
        }
        return published;
    }

    private void markPublished(UUID id) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                tx.executeWithoutResult(s -> {
                    Auction a = auctions.findById(id).orElseThrow();
                    a.markWonEventPublished();
                    auctions.saveAndFlush(a);
                });
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                // retry
            }
        }
    }
}
