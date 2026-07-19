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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BinanceKlineClient implements WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(BinanceKlineClient.class);
    private static final Duration STREAM_STALE_AFTER = Duration.ofSeconds(20);

    private final CandleStore candleStore;
    private final MarketDataStateService marketState;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String streamUrl;
    private final String restUrl;
    private final List<String> intervals;
    private final List<String> symbols;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Instant> lastMessage = new AtomicReference<>();
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final StringBuilder fragments = new StringBuilder();
    private volatile WebSocket webSocket;
    private volatile boolean shuttingDown;

    public BinanceKlineClient(CandleStore candleStore,
                              MarketDataStateService marketState,
                              ObjectMapper objectMapper,
                              @Value("${aegis.binance.enabled:true}") boolean enabled,
                              @Value("${aegis.binance.stream-url:}") String streamUrl,
                              @Value("${aegis.binance.rest-url:https://api.binance.com}") String restUrl,
                              @Value("${aegis.market.intervals:1m}") String intervals,
                              @Value("${aegis.market.symbols:BTCUSDT}") String symbols) {
        this.candleStore = candleStore;
        this.marketState = marketState;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.symbols = parseCsv(symbols, true);
        this.intervals = parseCsv(intervals, false);
        if (this.symbols.isEmpty() || this.intervals.isEmpty()) {
            throw new IllegalArgumentException("At least one Binance symbol and timeframe are required");
        }
        this.streamUrl = streamUrl == null || streamUrl.isBlank()
                ? combinedStreamUrl(this.symbols, this.intervals) : streamUrl;
        this.restUrl = restUrl.replaceAll("/+$", "");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "binance-market-stream-supervisor");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @PostConstruct
    void connect() {
        if (!enabled) {
            marketState.connectionChanged("DISABLED");
            log.info("Binance public market-data ingestion disabled");
            return;
        }
        for (String symbol : symbols) for (String interval : intervals) backfill(symbol, interval);
        openSocket();
        scheduler.scheduleAtFixedRate(this::watchdog, 5, 5, TimeUnit.SECONDS);
    }

    private void openSocket() {
        if (shuttingDown) return;
        marketState.connectionChanged("CONNECTING");
        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(streamUrl), this)
                .thenAccept(socket -> this.webSocket = socket)
                .exceptionally(error -> {
                    log.error("Unable to connect to Binance public stream", error);
                    marketState.connectionChanged("DISCONNECTED");
                    scheduleReconnect();
                    return null;
                });
    }

    @Override
    public void onOpen(WebSocket socket) {
        this.webSocket = socket;
        reconnectAttempt.set(0);
        reconnectScheduled.set(false);
        lastMessage.set(Instant.now());
        marketState.connectionChanged("CONNECTED");
        log.info("Connected to Binance public combined stream for {} symbols and {} timeframes",
                symbols.size(), intervals.size());
        socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
        String payload = null;
        synchronized (fragments) {
            fragments.append(data);
            if (last) {
                payload = fragments.toString();
                fragments.setLength(0);
            }
        }
        if (payload != null) process(payload);
        socket.request(1);
        return null;
    }

    private void process(String payload) {
        Instant receivedAt = Instant.now();
        lastMessage.set(receivedAt);
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode event = root.has("data") ? root.path("data") : root;
            String stream = root.path("stream").asText("");
            String streamSymbol = stream.contains("@") ? stream.substring(0, stream.indexOf('@')).toUpperCase() : "";
            String eventType = event.path("e").asText("");
            if (event.has("k")) {
                Candle candle = candle(event.path("k"));
                candleStore.upsert(candle);
                marketState.onKlinePrice(candle.symbol(), candle.close(), eventTime(event, receivedAt), receivedAt);
            } else if ("aggTrade".equals(eventType)) {
                marketState.onAggregateTrade(symbol(event, streamSymbol), event.path("a").asLong(),
                        decimal(event, "p"), decimal(event, "q"), event.path("m").asBoolean(),
                        eventTime(event, receivedAt), receivedAt);
            } else if ("depthUpdate".equals(eventType) || event.has("lastUpdateId")) {
                JsonNode bids = event.has("bids") ? event.path("bids") : event.path("b");
                JsonNode asks = event.has("asks") ? event.path("asks") : event.path("a");
                long updateId = event.has("lastUpdateId") ? event.path("lastUpdateId").asLong() : event.path("u").asLong();
                marketState.onDepth(symbol(event, streamSymbol), updateId, depthQuantity(bids), depthQuantity(asks),
                        eventTime(event, receivedAt), receivedAt);
            } else if (event.has("b") && event.has("B") && event.has("a") && event.has("A")) {
                marketState.onBookTicker(symbol(event, streamSymbol), event.path("u").asLong(),
                        decimal(event, "b"), decimal(event, "B"), decimal(event, "a"), decimal(event, "A"),
                        eventTime(event, receivedAt), receivedAt);
            }
        } catch (RuntimeException | java.io.IOException exception) {
            log.warn("Rejected Binance public market-data payload", exception);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
        if (!shuttingDown) {
            log.warn("Binance public stream closed: {} {}", statusCode, reason);
            marketState.connectionChanged("DISCONNECTED");
            scheduleReconnect();
        }
        return WebSocket.Listener.super.onClose(socket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        if (!shuttingDown) {
            log.error("Binance public stream error", error);
            marketState.connectionChanged("DISCONNECTED");
            scheduleReconnect();
        }
    }

    private void watchdog() {
        if (shuttingDown || !enabled) return;
        Instant last = lastMessage.get();
        if (last != null && Duration.between(last, Instant.now()).compareTo(STREAM_STALE_AFTER) <= 0) return;
        marketState.connectionChanged("STALE");
        WebSocket socket = webSocket;
        if (socket != null) socket.abort();
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (shuttingDown || !reconnectScheduled.compareAndSet(false, true)) return;
        int attempt = reconnectAttempt.incrementAndGet();
        long delaySeconds = Math.min(60, 1L << Math.min(6, attempt - 1));
        log.warn("Scheduling Binance stream reconnect attempt {} in {} seconds", attempt, delaySeconds);
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            openSocket();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void disconnect() {
        shuttingDown = true;
        marketState.connectionChanged("STOPPED");
        WebSocket socket = webSocket;
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        scheduler.shutdownNow();
    }

    private void backfill(String symbol, String interval) {
        String url = restUrl + "/api/v3/klines?symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "&interval=" + URLEncoder.encode(interval, StandardCharsets.UTF_8) + "&limit=250";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) {
                log.warn("Binance backfill rejected for {} {} with HTTP {}", symbol, interval, response.statusCode());
                return;
            }
            try {
                int rows = 0;
                for (JsonNode row : objectMapper.readTree(response.body())) {
                    candleStore.upsert(new Candle(symbol, interval, Instant.ofEpochMilli(row.get(0).asLong()),
                            Instant.ofEpochMilli(row.get(6).asLong()), decimal(row, 1), decimal(row, 2),
                            decimal(row, 3), decimal(row, 4), decimal(row, 5), true));
                    rows++;
                }
                log.info("Backfilled {} candles for {} {}", rows, symbol, interval);
            } catch (RuntimeException | java.io.IOException exception) {
                log.warn("Rejected Binance backfill for {} {}", symbol, interval, exception);
            }
        }).exceptionally(error -> {
            log.warn("Unable to backfill Binance candles for {} {}", symbol, interval, error);
            return null;
        });
    }

    private Candle candle(JsonNode kline) {
        if (kline.isMissingNode() || kline.path("s").asText().isBlank()) {
            throw new IllegalArgumentException("Binance payload does not contain a kline");
        }
        return new Candle(kline.path("s").asText(), kline.path("i").asText(),
                Instant.ofEpochMilli(kline.path("t").asLong()), Instant.ofEpochMilli(kline.path("T").asLong()),
                decimal(kline, "o"), decimal(kline, "h"), decimal(kline, "l"), decimal(kline, "c"),
                decimal(kline, "v"), kline.path("x").asBoolean());
    }

    private static Instant eventTime(JsonNode event, Instant fallback) {
        long epochMillis = event.path("E").asLong(event.path("T").asLong(0));
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : fallback;
    }

    private static String symbol(JsonNode event, String streamSymbol) {
        String value = event.path("s").asText(streamSymbol);
        if (value.isBlank()) throw new IllegalArgumentException("Binance event has no symbol");
        return value.toUpperCase();
    }

    private static BigDecimal depthQuantity(JsonNode levels) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode level : levels) total = total.add(new BigDecimal(level.get(1).asText()));
        return total;
    }

    private static BigDecimal decimal(JsonNode node, String field) { return new BigDecimal(node.path(field).asText()); }
    private static BigDecimal decimal(JsonNode row, int index) { return new BigDecimal(row.get(index).asText()); }

    private static List<String> parseCsv(String values, boolean upperCase) {
        return Arrays.stream(values.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(value -> upperCase ? value.toUpperCase() : value).distinct().toList();
    }

    private static String combinedStreamUrl(List<String> symbols, List<String> intervals) {
        List<String> streams = new java.util.ArrayList<>();
        for (String symbol : symbols) {
            String normalized = symbol.toLowerCase();
            streams.add(normalized + "@bookTicker");
            streams.add(normalized + "@aggTrade");
            streams.add(normalized + "@depth20@100ms");
            for (String interval : intervals) streams.add(normalized + "@kline_" + interval);
        }
        return "wss://stream.binance.com:9443/stream?streams=" + String.join("/", streams);
    }
}
