package com.akshit.auctionservice.controller;

import com.akshit.auctionservice.client.OrderTrackingClient;
import com.akshit.auctionservice.dto.Dtos.*;
import com.akshit.auctionservice.model.AuctionStatus;
import com.akshit.auctionservice.service.AuctionService;
import com.akshit.auctionservice.service.AuctionUpdates;
import com.akshit.auctionservice.service.BiddingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final AuctionService auctionService;
    private final BiddingService biddingService;
    private final AuctionUpdates updates;
    private final OrderTrackingClient orders;

    public AuctionController(AuctionService auctionService, BiddingService biddingService, AuctionUpdates updates,
                             OrderTrackingClient orders) {
        this.auctionService = auctionService;
        this.biddingService = biddingService;
        this.updates = updates;
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<AuctionView> create(@Valid @RequestBody CreateAuctionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AuctionView.of(auctionService.create(request)));
    }

    @GetMapping
    public List<AuctionView> open() {
        return auctionService.open().stream().map(AuctionView::of).toList();
    }

    @GetMapping("/{id}")
    public AuctionView get(@PathVariable UUID id) {
        return AuctionView.of(auctionService.get(id));
    }

    @GetMapping("/{id}/bids")
    public List<BidView> bids(@PathVariable UUID id) {
        return auctionService.recentBids(id).stream().map(BidView::of).toList();
    }

    /**
     * 200 for accepted bids (including immediately outbid ones), 422 if too low, 403 if the seller bids on their
     * own item, 409 if the auction has ended.
     */
    @PostMapping("/{id}/bids")
    public ResponseEntity<BidResult> bid(@PathVariable UUID id, @Valid @RequestBody PlaceBidRequest request) {
        BidResult result = biddingService.placeBid(id, request);
        HttpStatus status = switch (result.outcome()) {
            case TOO_LOW -> HttpStatus.UNPROCESSABLE_ENTITY;
            case OWN_AUCTION -> HttpStatus.FORBIDDEN;
            case AUCTION_CLOSED -> HttpStatus.CONFLICT;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping(path = "/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable UUID id) {
        auctionService.get(id);
        return updates.watch(id);
    }

    /** The winner's order as it moves through the SAGA (inventory -> payment -> completed, or compensated). */
    @GetMapping("/{id}/order")
    public ResponseEntity<OrderTrackingClient.Tracking> order(@PathVariable UUID id) {
        if (auctionService.get(id).getStatus() != AuctionStatus.SOLD) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        return ResponseEntity.ok(orders.track(id));
    }
}
