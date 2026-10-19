package com.akshit.auctionservice.repository;

import com.akshit.auctionservice.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    Optional<Bid> findByAuctionIdAndIdempotencyKey(UUID auctionId, String idempotencyKey);

    List<Bid> findByAuctionIdOrderBySequenceAsc(UUID auctionId);

    List<Bid> findTop20ByAuctionIdOrderBySequenceDesc(UUID auctionId);
}
