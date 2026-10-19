package com.akshit.auctionservice.service;

import java.util.UUID;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(UUID id) {
        super("auction " + id + " not found");
    }
}
