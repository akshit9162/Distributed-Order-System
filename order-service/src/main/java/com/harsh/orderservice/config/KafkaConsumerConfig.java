package com.harsh.orderservice.config;

import com.harsh.orderservice.events.AuctionWonEvent;
import com.harsh.orderservice.events.InventoryRejectedEvent;
import com.harsh.orderservice.events.InventoryReservedEvent;
import com.harsh.orderservice.events.PaymentConfirmedEvent;
import com.harsh.orderservice.events.PaymentRejectedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Each service only has its OWN copy of the event record classes (there is no
 * shared library between services - that's intentional, it's how you avoid
 * coupling independently-deployable microservices to a shared JAR). That
 * means we can't rely on Kafka's default type-header matching, since the
 * header would carry the producer's fully-qualified class name, which
 * doesn't exist in this service's classpath. Instead, each listener factory
 * is told explicitly which local class to deserialize into.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.harsh.orderservice.events");
        return props;
    }

    /**
     * Retries a failing listener 3 times (1s apart), then publishes the raw
     * record to "<topic>.DLT" instead of retrying forever or dropping it
     * silently. Lets an operator inspect and decide whether to replay it.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factoryFor(Class<T> type, DefaultErrorHandler errorHandler) {
        Map<String, Object> props = baseConsumerProps();
        ConsumerFactory<String, T> cf = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(type, false));
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> inventoryReservedListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(InventoryReservedEvent.class, errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryRejectedEvent> inventoryRejectedListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(InventoryRejectedEvent.class, errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmedEvent> paymentConfirmedListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(PaymentConfirmedEvent.class, errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRejectedEvent> paymentRejectedListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(PaymentRejectedEvent.class, errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuctionWonEvent> auctionWonListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(AuctionWonEvent.class, errorHandler);
    }
}
