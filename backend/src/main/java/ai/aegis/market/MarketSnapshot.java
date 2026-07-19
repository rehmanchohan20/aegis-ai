package ai.aegis.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MarketSnapshot(
        String symbol,
        Instant snapshotTime,
        Instant exchangeTime,
        Instant receivedAt,
        BigDecimal lastPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal spreadBps,
        BigDecimal weightedMidPrice,
        BigDecimal bidDepth,
        BigDecimal askDepth,
        BigDecimal orderBookImbalance,
        BigDecimal aggressiveBuyVolume,
        BigDecimal aggressiveSellVolume,
        BigDecimal cumulativeVolumeDelta,
        int tradeCount,
        BigDecimal tradeIntensity,
        BigDecimal averageTradeSize,
        BigDecimal realizedVolatility,
        BigDecimal volumeAcceleration,
        long ingestionLatencyMs,
        String connectionStatus,
        String dataQuality,
        Map<String, String> featureStatus,
        Long lastTradeId,
        Long lastDepthUpdateId
) {
    public MarketSnapshot {
        featureStatus = Map.copyOf(featureStatus == null ? Map.of() : featureStatus);
    }
}
