package ai.aegis.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

@Component
public class BinanceKlineClient implements WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(BinanceKlineClient.class);

    private final CandleStore candleStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final boolean enabled;
    private final String streamUrl;
    private WebSocket webSocket;

    public BinanceKlineClient(
            CandleStore candleStore,
            ObjectMapper objectMapper,
            @Value("${aegis.binance.enabled:true}") boolean enabled,
            @Value("${aegis.binance.stream-url:wss://stream.binance.com:9443/ws/btcusdt@kline_1m}") String streamUrl) {
        this.candleStore = candleStore;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.streamUrl = streamUrl;
    }

    @PostConstruct
    void connect() {
        if (!enabled) {
            log.info("Binance ingestion disabled");
            return;
        }
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(streamUrl), this)
                .thenAccept(socket -> {
                    this.webSocket = socket;
                    log.info("Connected to Binance stream {}", streamUrl);
                })
                .exceptionally(error -> {
                    log.error("Unable to connect to Binance stream", error);
                    return null;
                });
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            JsonNode kline = objectMapper.readTree(data.toString()).path("k");
            Candle candle = new Candle(
                    kline.path("s").asText(),
                    kline.path("i").asText(),
                    Instant.ofEpochMilli(kline.path("t").asLong()),
                    Instant.ofEpochMilli(kline.path("T").asLong()),
                    new BigDecimal(kline.path("o").asText()),
                    new BigDecimal(kline.path("h").asText()),
                    new BigDecimal(kline.path("l").asText()),
                    new BigDecimal(kline.path("c").asText()),
                    new BigDecimal(kline.path("v").asText()),
                    kline.path("x").asBoolean()
            );
            candleStore.upsert(candle);
        } catch (Exception exception) {
            log.warn("Rejected Binance payload", exception);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.warn("Binance stream closed: {} {}", statusCode, reason);
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("Binance stream error", error);
    }

    @PreDestroy
    void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }
}