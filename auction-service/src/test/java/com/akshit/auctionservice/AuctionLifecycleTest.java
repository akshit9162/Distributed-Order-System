package com.akshit.auctionservice;

import com.akshit.auctionservice.dto.Dtos.CreateAuctionRequest;
import com.akshit.auctionservice.events.AuctionWonEvent;
import com.akshit.auctionservice.kafka.AuctionEventProducer;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.AuctionStatus;
import com.akshit.auctionservice.repository.AuctionRepository;
import com.akshit.auctionservice.repository.BidRepository;
import com.akshit.auctionservice.service.AuctionCloser;
import com.akshit.auctionservice.service.AuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuctionLifecycleTest {

    @Autowired private MockMvc http;
    @Autowired private AuctionService auctionService;
    @Autowired private AuctionRepository auctions;
    @Autowired private BidRepository bids;
    @Autowired private AuctionCloser closer;
    @MockBean private AuctionEventProducer producer;

    @BeforeEach
    void reset() {
        bids.deleteAll();
        auctions.deleteAll();
    }

    private UUID create(String start, long seconds) {
        return auctionService.create(new CreateAuctionRequest("seller-1", "camera-1", "Camera",
                new BigDecimal(start), seconds)).getId();
    }

    private String bidJson(String key, String bidder, String max) {
        return "{\"idempotencyKey\":\"%s\",\"bidderId\":\"%s\",\"maxAmount\":%s}".formatted(key, bidder, max);
    }

    @Test
    void bidding_over_http_returns_outcomes_and_hides_the_leaders_maximum() throws Exception {
        UUID id = create("10.00", 3600);

        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("a1", "alice", "50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("LEADING"))
                .andExpect(jsonPath("$.currentPrice").value(10.00));
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("b1", "bob", "30.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("OUTBID"))
                .andExpect(jsonPath("$.leading").value(false))
                .andExpect(jsonPath("$.currentPrice").value(31.00));
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("c1", "carol", "31.50")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.outcome").value("TOO_LOW"))
                .andExpect(jsonPath("$.minimumNextBid").value(32.00));

        http.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderId").value("alice"))
                .andExpect(jsonPath("$.leaderMax").doesNotExist());
    }

    @Test
    void seller_cannot_bid_on_their_own_item() throws Exception {
        UUID id = create("10.00", 3600);   // seller-1
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("s", "seller-1", "99.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.outcome").value("OWN_AUCTION"));
        assertEquals(0, auctions.findById(id).orElseThrow().getBidCount());
    }

    @Test
    void invalid_and_unknown_requests_are_rejected() throws Exception {
        UUID id = create("10.00", 3600);
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("x", "alice", "10.001")))
                .andExpect(status().isBadRequest());
        http.perform(get("/auctions/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void ended_auction_is_sold_to_the_leader_and_handed_to_the_order_saga() throws Exception {
        UUID id = create("10.00", 3600);
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("a", "alice", "50.00")));
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("b", "bob", "40.00")));
        endNow(id);

        assertEquals(1, closer.closeDueAuctions());
        assertEquals(1, closer.publishPendingWins());

        Auction a = auctions.findById(id).orElseThrow();
        assertEquals(AuctionStatus.SOLD, a.getStatus());
        assertTrue(a.isWonEventPublished());
        var event = ArgumentCaptor.forClass(AuctionWonEvent.class);
        verify(producer).publishWon(event.capture());
        assertEquals("alice", event.getValue().winnerId());
        assertEquals(0, new BigDecimal("41.00").compareTo(event.getValue().price()));   // 40 + 1.00, not her 50

        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("late", "carol", "99.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("AUCTION_CLOSED"));
    }

    @Test
    void auction_with_no_bids_ends_unsold_and_publishes_nothing() throws Exception {
        UUID id = create("10.00", 3600);
        endNow(id);

        closer.closeDueAuctions();
        closer.publishPendingWins();

        assertEquals(AuctionStatus.UNSOLD, auctions.findById(id).orElseThrow().getStatus());
        verify(producer, never()).publishWon(any());
    }

    @Test
    void failed_publish_is_retried_until_kafka_accepts_it() throws Exception {
        UUID id = create("10.00", 3600);
        http.perform(post("/auctions/{id}/bids", id).contentType(MediaType.APPLICATION_JSON).content(bidJson("a", "alice", "20.00")));
        endNow(id);
        doThrow(new RuntimeException("broker down")).doNothing().when(producer).publishWon(any());

        closer.closeDueAuctions();
        assertEquals(0, closer.publishPendingWins());   // Kafka down: stays pending
        assertFalse(auctions.findById(id).orElseThrow().isWonEventPublished());

        assertEquals(1, closer.publishPendingWins());   // next run: delivered
        assertTrue(auctions.findById(id).orElseThrow().isWonEventPublished());
        verify(producer, times(2)).publishWon(any());
    }

    /** Moves the end time into the past (the API only allows future end times). */
    private void endNow(UUID id) throws Exception {
        var field = Auction.class.getDeclaredField("endsAt");
        field.setAccessible(true);
        Auction a = auctions.findById(id).orElseThrow();
        field.set(a, Instant.now().minusSeconds(1));
        auctions.save(a);
    }
}
