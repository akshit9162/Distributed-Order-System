package com.akshit.auctionservice.service;

import com.akshit.auctionservice.dto.Dtos.AuctionView;
import com.akshit.auctionservice.repository.AuctionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live auction updates over Server-Sent Events (observer pattern): browsers watching an auction get its new public
 * state after every accepted bid and when it closes. One-way server-to-browser updates are all an auction page
 * needs, so SSE is simpler than WebSockets here (plain HTTP, automatic browser reconnect).
 */
@Component
public class AuctionUpdates {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> watchers = new ConcurrentHashMap<>();
    private final AuctionRepository auctions;

    public AuctionUpdates(AuctionRepository auctions) {
        this.auctions = auctions;
    }

    public SseEmitter watch(UUID auctionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = watchers.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        auctions.findById(auctionId).ifPresent(a -> send(emitter, AuctionView.of(a)));
        return emitter;
    }

    public void publish(UUID auctionId) {
        List<SseEmitter> list = watchers.get(auctionId);
        if (list == null || list.isEmpty()) return;
        auctions.findById(auctionId).map(AuctionView::of).ifPresent(view -> list.forEach(e -> send(e, view)));
    }

    public int watcherCount() {
        return watchers.values().stream().mapToInt(List::size).sum();
    }

    private static void send(SseEmitter emitter, AuctionView view) {
        try {
            emitter.send(SseEmitter.event().name("auction").data(view));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);   // client went away; onError removes it
        }
    }
}
