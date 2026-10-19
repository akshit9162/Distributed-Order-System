package com.akshit.auctionservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.UUID;

/**
 * Asks order-service how the winner's order is doing, so the auction page can show the SAGA progressing.
 * The browser only talks to auction-service; this server-to-server call avoids cross-origin requests from the page.
 */
@Component
public class OrderTrackingClient {

    public enum State { FOUND, NOT_CREATED_YET, ORDER_SERVICE_UNAVAILABLE }

    public record Tracking(State state, UUID orderId, String orderStatus, Instant updatedAt) {
    }

    private record OrderJson(UUID id, String status, Instant updatedAt) {
    }

    private final RestClient http;

    public OrderTrackingClient(RestClient.Builder builder, @Value("${order-service-url}") String baseUrl) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(1000);
        timeouts.setReadTimeout(2000);
        this.http = builder.baseUrl(baseUrl).requestFactory(timeouts).build();
    }

    public Tracking track(UUID auctionId) {
        try {
            OrderJson order = http.get().uri("/orders/by-key/{key}", "auction-" + auctionId).retrieve().body(OrderJson.class);
            return new Tracking(State.FOUND, order.id(), order.status(), order.updatedAt());
        } catch (HttpClientErrorException.NotFound e) {
            return new Tracking(State.NOT_CREATED_YET, null, null, null);   // event still on its way through Kafka
        } catch (RestClientException e) {
            return new Tracking(State.ORDER_SERVICE_UNAVAILABLE, null, null, null);
        }
    }
}
