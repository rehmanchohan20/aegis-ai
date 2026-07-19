package ai.aegis.structure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketStructureSnapshot(
        UUID id,
        String symbol,
        String interval,
        Instant calculatedAt,
        String structureState,
        String regime,
        BigDecimal supportPrice,
        BigDecimal resistancePrice,
        BigDecimal atr,
        BigDecimal atrUpperBand,
        BigDecimal atrLowerBand,
        boolean consolidation,
        String breakoutState,
        List<Pivot> pivots,
        List<TrendLine> trendLines,
        List<Zone> zones,
        List<Marker> markers,
        List<String> warnings
) {
    public MarketStructureSnapshot {
        pivots = List.copyOf(pivots == null ? List.of() : pivots);
        trendLines = List.copyOf(trendLines == null ? List.of() : trendLines);
        zones = List.copyOf(zones == null ? List.of() : zones);
        markers = List.copyOf(markers == null ? List.of() : markers);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public record Pivot(int index, Instant time, BigDecimal price, String type, int strength) { }

    public record TrendLine(String symbol, String timeframe, String type,
                            Instant startTime, BigDecimal startPrice,
                            Instant endTime, BigDecimal endPrice,
                            BigDecimal slopePerCandle, int confirmedTouches,
                            BigDecimal currentDistancePercent, String breakStatus,
                            BigDecimal confidenceScore) { }

    public record Zone(String type, BigDecimal lower, BigDecimal upper, int touches,
                       BigDecimal confidenceScore) { }

    public record Marker(Instant time, BigDecimal price, String type, String direction,
                         BigDecimal confidenceScore) { }
}
