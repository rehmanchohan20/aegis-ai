package ai.aegis.market;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean closed
) {
    public Candle {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (interval == null || interval.isBlank()) throw new IllegalArgumentException("interval is required");
        if (openTime == null || closeTime == null) throw new IllegalArgumentException("timestamps are required");
        if (open == null || high == null || low == null || close == null || volume == null) {
            throw new IllegalArgumentException("OHLCV values are required");
        }
    }
}
