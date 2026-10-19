package com.akshit.auctionservice.service;

import com.akshit.auctionservice.dto.Dtos.CreateAuctionRequest;
import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.AuctionStatus;
import com.akshit.auctionservice.model.Bid;
import com.akshit.auctionservice.repository.AuctionRepository;
import com.akshit.auctionservice.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AuctionService {

    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final Clock clock;

    public AuctionService(AuctionRepository auctions, BidRepository bids, Clock clock) {
        this.auctions = auctions;
        this.bids = bids;
        this.clock = clock;
    }

    public Auction create(CreateAuctionRequest r) {
        return auctions.save(new Auction(r.sellerId(), r.productId(), r.title(),
                r.startPrice().setScale(2, RoundingMode.UNNECESSARY), clock.instant().plusSeconds(r.durationSeconds())));
    }

    public Auction get(UUID id) {
        return auctions.findById(id).orElseThrow(() -> new AuctionNotFoundException(id));
    }

    public List<Auction> open() {
        return auctions.findTop50ByStatusOrderByEndsAtAsc(AuctionStatus.OPEN);
    }

    public List<Bid> recentBids(UUID id) {
        get(id);
        return bids.findTop20ByAuctionIdOrderBySequenceDesc(id);
    }
}
