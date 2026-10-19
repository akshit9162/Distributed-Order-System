package com.harsh.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // One topic per event type. This is simpler to reason about and configure
    // than a shared topic carrying multiple event shapes (which would need a
    // type-discriminator + custom deserialization). Each service subscribes
    // only to the topics it actually cares about.
    public static final String ORDER_CREATED_TOPIC = "order-created";
    public static final String ORDER_ROLLED_BACK_TOPIC = "order-rolled-back";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    public static final String INVENTORY_REJECTED_TOPIC = "inventory-rejected";
    public static final String PAYMENT_CONFIRMED_TOPIC = "payment-confirmed";
    public static final String PAYMENT_REJECTED_TOPIC = "payment-rejected";
    public static final String AUCTION_WON_TOPIC = "auction-won";

    @Bean public NewTopic orderCreatedTopic() { return TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic orderRolledBackTopic() { return TopicBuilder.name(ORDER_ROLLED_BACK_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic inventoryReservedTopic() { return TopicBuilder.name(INVENTORY_RESERVED_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic inventoryRejectedTopic() { return TopicBuilder.name(INVENTORY_REJECTED_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentConfirmedTopic() { return TopicBuilder.name(PAYMENT_CONFIRMED_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic auctionWonTopic() { return TopicBuilder.name(AUCTION_WON_TOPIC).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentRejectedTopic() { return TopicBuilder.name(PAYMENT_REJECTED_TOPIC).partitions(3).replicas(1).build(); }
}
