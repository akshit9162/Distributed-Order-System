package com.akshit.auctionservice;

import com.akshit.auctionservice.domain.ProxyBidding;
import com.akshit.auctionservice.dto.Dtos.BidResult;
import com.akshit.auctionservice.dto.Dtos.CreateAuctionRequest;
import com.akshit.auctionservice.dto.Dtos.PlaceBidRequest;
import com.akshit.auctionservice.kafka.AuctionEventProducer;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.Bid;
import com.akshit.auctionservice.repository.AuctionRepository;
import com.akshit.auctionservice.repository.BidRepository;
import com.akshit.auctionservice.service.AuctionService;
import com.akshit.auctionservice.service.BiddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The guarantee that matters for a busy auction: under heavy concurrent bidding, the end state is exactly what
 * the proxy-bidding rules give when the accepted bids are applied one at a time, in the order they committed.
 * No lost updates, no bid applied twice, and the bid sequence has no gaps.
 */
@SpringBootTest
@ActiveProfiles("test")
class BiddingConcurrencyTest {

    private static final int BIDS = 1000;
    private static final int THREADS = 32;
    private static final int BIDDERS = 40;

    @Autowired private AuctionService auctionService;
    @Autowired private BiddingService biddingService;
    @Autowired private AuctionRepository auctions;
    @Autowired private BidRepository bids;
    @MockBean private AuctionEventProducer producer;

    @Test
    void concurrent_bids_end_exactly_where_serial_replay_ends() throws Exception {
        Auction auction = auctionService.create(new CreateAuctionRequest("seller", "p-1", "Vintage camera",
                new BigDecimal("1.00"), 3600));
        UUID id = auction.getId();
        long conflictsBefore = biddingService.conflicts();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BidResult>> results = new ArrayList<>();
        Random rng = new Random(11);
        for (int i = 0; i < BIDS; i++) {
            String bidder = "bidder-" + rng.nextInt(BIDDERS);
            // An escalating bidding war: each bid is a bit higher than the last one submitted, but 32 threads
            // deliver them in a scrambled order, so they collide on the same row and some arrive too low.
            BigDecimal max = BigDecimal.valueOf(100 + i * 300L + rng.nextInt(300), 2);
            String key = "k-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return biddingService.placeBid(id, new PlaceBidRequest(key, bidder, max));
            }));
        }
        start.countDown();   // all threads hit the same auction at once
        int accepted = 0;
        for (Future<BidResult> f : results) if (f.get(60, TimeUnit.SECONDS).accepted()) accepted++;
        pool.shutdown();

        Auction end = auctions.findById(id).orElseThrow();
        List<Bid> history = bids.findByAuctionIdOrderBySequenceAsc(id);

        // 1. Every accepted bid is recorded exactly once, with gap-free sequence numbers.
        assertEquals(accepted, history.size());
        assertEquals(accepted, end.getBidCount());
        for (int i = 0; i < history.size(); i++) assertEquals(i + 1, history.get(i).getSequence());

        // 2. Replaying the recorded bids one at a time reproduces the final state exactly.
        ProxyBidding.State s = ProxyBidding.State.opening(end.getStartPrice());
        for (Bid b : history) {
            ProxyBidding.Result r = ProxyBidding.apply(s, b.getBidderId(), b.getMaxAmount());
            assertTrue(r.accepted(), "replayed bid " + b.getSequence() + " was rejected");
            assertEquals(b.getOutcome(), r.outcome());
            assertEquals(0, b.getPriceAfter().compareTo(r.state().currentPrice()));
            s = r.state();
        }
        assertEquals(s.leaderId(), end.getLeaderId());
        assertEquals(0, s.currentPrice().compareTo(end.getCurrentPrice()));
        assertEquals(0, s.leaderMax().compareTo(end.getLeaderMax()));

        // 3. The winner holds the highest maximum among accepted bids.
        BigDecimal highest = history.stream().map(Bid::getMaxAmount).max(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, highest.compareTo(end.getLeaderMax()));

        long conflicts = biddingService.conflicts() - conflictsBefore;
        System.out.printf("concurrency: %d bids, %d accepted, %d optimistic-lock conflicts retried%n",
                BIDS, accepted, conflicts);
        assertTrue(conflicts > 0, "the test should actually create contention");
    }

    @Test
    void retried_bid_with_the_same_key_is_applied_once() {
        Auction auction = auctionService.create(new CreateAuctionRequest("seller", "p-2", "Lamp",
                new BigDecimal("5.00"), 3600));
        var request = new PlaceBidRequest("same-key", "alice", new BigDecimal("20.00"));

        BidResult first = biddingService.placeBid(auction.getId(), request);
        BidResult retry = biddingService.placeBid(auction.getId(), request);

        assertFalse(first.duplicate());
        assertTrue(retry.duplicate());
        assertEquals(1, bids.findByAuctionIdOrderBySequenceAsc(auction.getId()).size());
    }
}
