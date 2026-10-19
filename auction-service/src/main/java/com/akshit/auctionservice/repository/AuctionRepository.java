package com.akshit.auctionservice.repository;

import com.akshit.auctionservice.model.Auction;
import com.akshit.auctionservice.model.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    List<Auction> findByStatusAndEndsAtLessThanEqual(AuctionStatus status, Instant now);

    List<Auction> findByStatusAndWonEventPublishedFalse(AuctionStatus status);

    List<Auction> findTop50ByStatusOrderByEndsAtAsc(AuctionStatus status);
}
