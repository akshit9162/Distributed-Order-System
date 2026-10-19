package com.akshit.auctionservice;

import com.akshit.auctionservice.domain.BidIncrements;
import com.akshit.auctionservice.domain.ProxyBidding;
import com.akshit.auctionservice.domain.ProxyBidding.Outcome;
import com.akshit.auctionservice.domain.ProxyBidding.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProxyBiddingTest {

    private static BigDecimal $(String v) {
        return new BigDecimal(v);
    }

    private static State open(String start) {
        return State.opening($(start));
    }

    private static ProxyBidding.Result bid(State s, String who, String max) {
        return ProxyBidding.apply(s, who, $(max));
    }

    @Test
    void first_bid_leads_at_the_start_price_not_at_its_maximum() {
        var r = bid(open("10.00"), "alice", "50.00");

        assertEquals(Outcome.LEADING, r.outcome());
        assertEquals($("10.00"), r.state().currentPrice());   // alice's 50 stays secret
        assertEquals("alice", r.state().leaderId());
    }

    @Test
    void first_bid_below_the_start_price_is_rejected() {
        assertEquals(Outcome.TOO_LOW, bid(open("10.00"), "alice", "9.99").outcome());
    }

    @Test
    void higher_maximum_takes_the_lead_one_increment_above_the_old_maximum() {
        var s = bid(open("10.00"), "alice", "50.00").state();

        var r = bid(s, "bob", "80.00");

        assertEquals(Outcome.LEADING, r.outcome());
        assertEquals("bob", r.state().leaderId());
        assertEquals($("51.00"), r.state().currentPrice());   // 50 + the 1.00 increment for 25-99.99
    }

    @Test
    void lower_maximum_is_outbid_immediately_and_pushes_the_price_up() {
        var s = bid(open("10.00"), "alice", "50.00").state();

        var r = bid(s, "bob", "30.00");

        assertEquals(Outcome.OUTBID, r.outcome());
        assertEquals("alice", r.state().leaderId());           // alice's proxy answered
        assertEquals($("31.00"), r.state().currentPrice());   // 30 + 1.00
    }

    @Test
    void price_never_exceeds_the_leaders_maximum() {
        var s = bid(open("10.00"), "alice", "50.00").state();

        var r = bid(s, "bob", "49.50");   // 49.50 + 1.00 would be 50.50

        assertEquals("alice", r.state().leaderId());
        assertEquals($("50.00"), r.state().currentPrice());
    }

    @Test
    void equal_maximum_loses_to_the_earlier_bid() {
        var s = bid(open("10.00"), "alice", "50.00").state();

        var r = bid(s, "bob", "50.00");

        assertEquals(Outcome.OUTBID, r.outcome());
        assertEquals("alice", r.state().leaderId());
        assertEquals($("50.00"), r.state().currentPrice());
    }

    @Test
    void leader_raising_their_own_maximum_does_not_raise_the_price() {
        var s = bid(bid(open("10.00"), "alice", "50.00").state(), "bob", "30.00").state();   // price 31

        var r = bid(s, "alice", "90.00");

        assertEquals(Outcome.MAX_RAISED, r.outcome());
        assertEquals($("31.00"), r.state().currentPrice());
        assertEquals($("90.00"), r.state().leaderMax());
    }

    @Test
    void bid_must_beat_the_price_by_at_least_one_increment() {
        var s = bid(bid(open("10.00"), "alice", "50.00").state(), "bob", "30.00").state();   // price 31

        assertEquals(Outcome.TOO_LOW, bid(s, "carol", "31.99").outcome());   // minimum is 32.00
        assertNotEquals(Outcome.TOO_LOW, bid(s, "carol", "32.00").outcome());
    }

    @ParameterizedTest
    @CsvSource({"0.50,0.05", "0.99,0.05", "1.00,0.25", "24.99,0.50", "25.00,1.00", "99.99,1.00",
            "100.00,2.50", "999.99,10.00", "1000.00,25.00", "4999.99,50.00", "5000.00,100.00", "250000,100.00"})
    void increments_follow_the_ebay_table(String price, String increment) {
        assertEquals($(increment), BidIncrements.at($(price)));
    }

    @Test
    void a_bidding_war_settles_one_increment_above_the_second_highest_maximum() {
        var s = open("1.00");
        String[][] bids = {{"a", "5.00"}, {"b", "12.00"}, {"c", "8.00"}, {"a", "20.00"}, {"d", "19.00"}, {"b", "30.00"}};
        for (String[] b : bids) s = bid(s, b[0], b[1]).state();

        assertEquals("b", s.leaderId());                      // highest maximum: 30
        assertEquals($("20.50"), s.currentPrice());          // second-highest 20 + 0.50 (the increment at 20)
    }
}
