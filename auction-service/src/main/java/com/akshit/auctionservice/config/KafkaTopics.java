package com.akshit.auctionservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String AUCTION_WON_TOPIC = "auction-won";

    @Bean
    public NewTopic auctionWonTopic() {
        return TopicBuilder.name(AUCTION_WON_TOPIC).partitions(3).replicas(1).build();
    }
}
