package com.akshit.auctionservice.kafka;

import com.akshit.auctionservice.config.KafkaTopics;
import com.akshit.auctionservice.events.AuctionWonEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AuctionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuctionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Blocks until Kafka acknowledges, so the caller only marks the event published once it really is. */
    public void publishWon(AuctionWonEvent event) throws Exception {
        kafkaTemplate.send(KafkaTopics.AUCTION_WON_TOPIC, event.auctionId().toString(), event).get(10, TimeUnit.SECONDS);
    }
}
