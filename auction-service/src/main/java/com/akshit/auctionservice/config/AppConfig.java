package com.akshit.auctionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected rather than calling Instant.now() directly, so time-dependent rules can be tested. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
