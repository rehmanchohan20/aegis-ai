package ai.aegis.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MarketDataStateService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataStateService.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final Duration STALE_AFTER = Duration.ofSeconds(5);

    private final Map<String, MutableMarketState> states = new ConcurrentHashMap<>();
    private final Map<String, MarketSnapshot> latest = new ConcurrentHashMap<>();
    private final MarketSnapshotStore store;
    private final LiveMarketStreamService liveStream;
    private final AtomicReference<String> connectionStatus = new AtomicReference<>("CONNECTING");

    public MarketDataStateService(MarketSnapshotStore store,
                                  LiveMarketStreamService liveStream,
                                  @Value("${aegis.market.symbols:BTCUSDT}") String configuredSymbols) {
        this.store = store;
        this.liveStream = liveStream;
        Arrays.stream(configuredSymbols.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(String::toUpperCase).distinct().forEach(symbol -> states.put(symbol, new MutableMarketState(symbol)));
        if (states.isEmpty()) throw new IllegalArgumentException("At least one market symbol is required");
    }

    public void connectionChanged(String status) {
        connectionStatus.set(status);
    }

    public void onBookTicker(String symbol, long updateId, BigDecimal bidPrice, BigDecimal bidQuantity,
                             BigDecimal askPrice, BigDecimal askQuantity, Instant exchangeTime, Instant receivedAt) {
        state(symbol).book(updateId, bidPrice, bidQuantity, askPrice, askQuantity, exchangeTime, receivedAt);
    }

    public void onDepth(String symbol, long updateId, BigDecimal bidDepth, BigDecimal askDepth,
                        Instant exchangeTime, Instant receivedAt) {
        state(symbol).depth(updateId, bidDepth, askDepth, exchangeTime, receivedAt);
    }

    public void onAggregateTrade(String symbol, long tradeId, BigDecimal price, BigDecimal quantity,
                                 boolean buyerWasMaker, Instant exchangeTime, Instant receivedAt) {
        state(symbol).trade(tradeId, price, quantity, buyerWasMaker, exchangeTime, receivedAt);
    }

    public void onKlinePrice(String symbol, BigDecimal price, Instant exchangeTime, Instant receivedAt) {
        state(symbol).price(price, exchangeTime, receivedAt);
    }

    public MarketSnapshot latest(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        return latest.get(symbol.toUpperCase());
    }

    public List<MarketSnapshot> allLatest() {
        return states.keySet().stream().sorted().map(latest::get).filter(java.util.Objects::nonNull).toList();
    }

    public Map<String, Object> status() {
        Instant newest = states.values().stream().map(MutableMarketState::lastMessage)
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        return Map.of("connectionStatus", connectionStatus.get(), "lastMessageTime", newest == null ? "" : newest,
                "configuredSymbols", List.copyOf(states.keySet()), "snapshotsAvailable", latest.size(),
                "dataQuality", latest.values().stream().collect(java.util.stream.Collectors.toMap(
                        MarketSnapshot::symbol, MarketSnapshot::dataQuality)));
    }

    @Scheduled(fixedRateString = "${aegis.market.snapshot-interval-ms:1000}")
    public void persistSnapshots() {
        Instant now = Instant.now();
        Instant bucket = now.truncatedTo(ChronoUnit.SECONDS);
        for (MutableMarketState state : states.values()) {
            MarketSnapshot snapshot = state.snapshot(bucket, now, connectionStatus.get());
            if (snapshot == null) continue;
            try {
                store.save(snapshot);
                latest.put(snapshot.symbol(), snapshot);
                liveStream.publish(snapshot);
            } catch (RuntimeException exception) {
                log.error("Failed to persist one-second market snapshot for {}", snapshot.symbol(), exception);
            }
        }
    }

    private MutableMarketState state(String symbol) {
        MutableMarketState state = states.get(symbol.toUpperCase());
        if (state == null) throw new IllegalArgumentException("Unconfigured market symbol " + symbol);
        return state;
    }

    private static final class MutableMarketState {
        private final String symbol;
        private final Deque<TimedPrice> prices = new ArrayDeque<>();
        private BigDecimal lastPrice;
        private BigDecimal bidPrice;
        private BigDecimal askPrice;
        private BigDecimal bidQuantity;
        private BigDecimal askQuantity;
        private BigDecimal bidDepth;
        private BigDecimal askDepth;
        private BigDecimal aggressiveBuy = BigDecimal.ZERO;
        private BigDecimal aggressiveSell = BigDecimal.ZERO;
        private BigDecimal cumulativeDelta = BigDecimal.ZERO;
        private BigDecimal previousWindowVolume = BigDecimal.ZERO;
        private int tradeCount;
        private long lastTradeId = -1;
        private long lastDepthUpdateId = -1;
        private long lastBookUpdateId = -1;
        private Instant exchangeTime;
        private Instant receivedAt;
        private Instant lastMessage;

        private MutableMarketState(String symbol) { this.symbol = symbol; }

        synchronized void book(long updateId, BigDecimal bid, BigDecimal bidQty, BigDecimal ask, BigDecimal askQty,
                               Instant eventTime, Instant arrivalTime) {
            if (updateId <= lastBookUpdateId || invalid(bid) || invalid(ask) || bid.compareTo(ask) > 0) return;
            lastBookUpdateId = updateId;
            bidPrice = bid;
            askPrice = ask;
            bidQuantity = positiveOrZero(bidQty);
            askQuantity = positiveOrZero(askQty);
            touch(eventTime, arrivalTime);
        }

        synchronized void depth(long updateId, BigDecimal bids, BigDecimal asks,
                                Instant eventTime, Instant arrivalTime) {
            if (updateId <= lastDepthUpdateId) return;
            lastDepthUpdateId = updateId;
            bidDepth = positiveOrZero(bids);
            askDepth = positiveOrZero(asks);
            touch(eventTime, arrivalTime);
        }

        synchronized void trade(long tradeId, BigDecimal price, BigDecimal quantity, boolean buyerWasMaker,
                                Instant eventTime, Instant arrivalTime) {
            if (tradeId <= lastTradeId || invalid(price) || invalid(quantity)) return;
            lastTradeId = tradeId;
            lastPrice = price;
            if (buyerWasMaker) aggressiveSell = aggressiveSell.add(quantity, MC);
            else aggressiveBuy = aggressiveBuy.add(quantity, MC);
            cumulativeDelta = cumulativeDelta.add(buyerWasMaker ? quantity.negate() : quantity, MC);
            tradeCount++;
            prices.addLast(new TimedPrice(eventTime, price));
            trimPrices(eventTime.minusSeconds(60));
            touch(eventTime, arrivalTime);
        }

        synchronized void price(BigDecimal price, Instant eventTime, Instant arrivalTime) {
            if (invalid(price)) return;
            if (exchangeTime == null || !eventTime.isBefore(exchangeTime)) {
                lastPrice = price;
                touch(eventTime, arrivalTime);
            }
        }

        synchronized Instant lastMessage() { return lastMessage; }

        synchronized MarketSnapshot snapshot(Instant bucket, Instant now, String connection) {
            if (lastPrice == null || exchangeTime == null || receivedAt == null) return null;
            trimPrices(now.minusSeconds(60));
            BigDecimal spread = bidPrice == null || askPrice == null ? null : askPrice.subtract(bidPrice, MC);
            BigDecimal midpoint = bidPrice == null || askPrice == null ? lastPrice
                    : bidPrice.add(askPrice, MC).divide(BigDecimal.valueOf(2), MC);
            BigDecimal spreadBps = spread == null || midpoint.signum() == 0 ? null
                    : spread.divide(midpoint, MC).multiply(BigDecimal.valueOf(10_000), MC);
            BigDecimal microPrice = weightedMid();
            BigDecimal imbalance = imbalance(bidDepth, askDepth);
            BigDecimal windowVolume = aggressiveBuy.add(aggressiveSell, MC);
            BigDecimal acceleration = previousWindowVolume.signum() == 0 ? BigDecimal.ZERO
                    : windowVolume.subtract(previousWindowVolume, MC).divide(previousWindowVolume, MC);
            previousWindowVolume = windowVolume;
            BigDecimal averageSize = tradeCount == 0 ? BigDecimal.ZERO
                    : windowVolume.divide(BigDecimal.valueOf(tradeCount), MC);
            long latency = Math.max(0, Duration.between(exchangeTime, receivedAt).toMillis());
            boolean stale = lastMessage == null || Duration.between(lastMessage, now).compareTo(STALE_AFTER) > 0;
            String quality = stale || !"CONNECTED".equals(connection) ? "STALE"
                    : bidPrice == null || askPrice == null || bidDepth == null || askDepth == null ? "DEGRADED" : "GOOD";
            Map<String, String> features = new LinkedHashMap<>();
            features.put("ticker", bidPrice == null ? "MISSING" : quality);
            features.put("tradeFlow", lastTradeId < 0 ? "MISSING" : quality);
            features.put("orderBookDepth", lastDepthUpdateId < 0 ? "MISSING" : quality);
            features.put("fundingRate", "UNSUPPORTED_SPOT_STREAM");
            features.put("openInterest", "UNSUPPORTED_SPOT_STREAM");
            features.put("liquidations", "UNSUPPORTED_SPOT_STREAM");
            MarketSnapshot result = new MarketSnapshot(symbol, bucket, exchangeTime, receivedAt, lastPrice,
                    bidPrice, askPrice, spreadBps, microPrice, bidDepth, askDepth, imbalance,
                    aggressiveBuy, aggressiveSell, cumulativeDelta, tradeCount, BigDecimal.valueOf(tradeCount),
                    averageSize, realizedVolatility(), acceleration, latency, connection, quality,
                    features, lastTradeId < 0 ? null : lastTradeId,
                    lastDepthUpdateId < 0 ? null : lastDepthUpdateId);
            aggressiveBuy = BigDecimal.ZERO;
            aggressiveSell = BigDecimal.ZERO;
            tradeCount = 0;
            return result;
        }

        private BigDecimal weightedMid() {
            if (bidPrice == null || askPrice == null || bidQuantity == null || askQuantity == null) return lastPrice;
            BigDecimal denominator = bidQuantity.add(askQuantity, MC);
            if (denominator.signum() == 0) return bidPrice.add(askPrice, MC).divide(BigDecimal.valueOf(2), MC);
            return askPrice.multiply(bidQuantity, MC).add(bidPrice.multiply(askQuantity, MC), MC)
                    .divide(denominator, MC);
        }

        private BigDecimal realizedVolatility() {
            if (prices.size() < 2) return BigDecimal.ZERO;
            double sumSquares = 0.0;
            TimedPrice previous = null;
            for (TimedPrice current : prices) {
                if (previous != null && previous.price().signum() > 0 && current.price().signum() > 0) {
                    double value = Math.log(current.price().doubleValue() / previous.price().doubleValue());
                    sumSquares += value * value;
                }
                previous = current;
            }
            return BigDecimal.valueOf(Math.sqrt(sumSquares));
        }

        private void trimPrices(Instant cutoff) {
            while (!prices.isEmpty() && prices.getFirst().time().isBefore(cutoff)) prices.removeFirst();
        }

        private void touch(Instant eventTime, Instant arrivalTime) {
            if (exchangeTime == null || !eventTime.isBefore(exchangeTime)) exchangeTime = eventTime;
            receivedAt = arrivalTime;
            lastMessage = arrivalTime;
        }

        private static BigDecimal imbalance(BigDecimal bids, BigDecimal asks) {
            if (bids == null || asks == null) return null;
            BigDecimal denominator = bids.add(asks, MC);
            return denominator.signum() == 0 ? BigDecimal.ZERO : bids.subtract(asks, MC).divide(denominator, MC);
        }

        private static boolean invalid(BigDecimal value) { return value == null || value.signum() <= 0; }
        private static BigDecimal positiveOrZero(BigDecimal value) {
            return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
        }
    }

    private record TimedPrice(Instant time, BigDecimal price) { }
}
