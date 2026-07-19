package ai.aegis.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveMarketStreamService {
    private static final Logger log = LoggerFactory.getLogger(LiveMarketStreamService.class);
    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String symbol) {
        String normalized = symbol == null || symbol.isBlank() ? "BTCUSDT" : symbol.toUpperCase();
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(0L);
        subscriptions.put(id, new Subscription(normalized, emitter));
        Runnable cleanup = () -> subscriptions.remove(id);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of(
                    "symbol", normalized, "connectedAt", Instant.now().toString())));
        } catch (IOException exception) {
            cleanup.run();
        }
        return emitter;
    }

    public void publish(MarketSnapshot snapshot) {
        List<Map.Entry<UUID, Subscription>> targets = subscriptions.entrySet().stream()
                .filter(entry -> entry.getValue().symbol().equals(snapshot.symbol())).toList();
        for (Map.Entry<UUID, Subscription> target : targets) {
            try {
                target.getValue().emitter().send(SseEmitter.event().name("snapshot")
                        .id(snapshot.symbol() + "-" + snapshot.snapshotTime().toEpochMilli()).data(snapshot));
            } catch (IOException | IllegalStateException exception) {
                subscriptions.remove(target.getKey());
                log.debug("Removed closed live market subscriber {}", target.getKey());
            }
        }
    }

    private record Subscription(String symbol, SseEmitter emitter) { }
}
