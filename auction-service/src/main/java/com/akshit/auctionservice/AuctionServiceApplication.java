package com.akshit.auctionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class AuctionServiceApplication {
    public static void main(String[] args) {
        // Auction end times are compared to "now" constantly; one clock zone everywhere (UTC) avoids a shifted
        // end time when the JVM and the database disagree about the local zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AuctionServiceApplication.class, args);
    }
}
