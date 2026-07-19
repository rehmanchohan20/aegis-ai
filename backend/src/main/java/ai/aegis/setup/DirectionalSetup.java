package ai.aegis.setup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectionalSetup(
        UUID id, String symbol, String timeframe, Instant evaluatedAt,
        String status, String direction, DirectionalBias directionalBias,
        VolumeProfile fixedRangeProfile, VolumeProfile sessionProfile,
        String structureState, String setupType, String volumeState,
        boolean breakOfStructureConfirmed, boolean breakoutConfirmed,
        boolean pullbackIntoValue, boolean breakoutRetest, boolean reclaimOrRejection,
        PriceZone entryZone, BigDecimal stopLoss, List<BigDecimal> takeProfitLevels,
        BigDecimal expectedRMultiple, BigDecimal stopHitFirstProbability,
        BigDecimal takeProfitHitFirstProbability, BigDecimal tradeQualityScore,
        String probabilitySource, List<ChartMarker> markers,
        List<String> confirmations, List<String> rejections, List<String> dataWarnings
) {
    public DirectionalSetup {
        takeProfitLevels = List.copyOf(takeProfitLevels == null ? List.of() : takeProfitLevels);
        markers = List.copyOf(markers == null ? List.of() : markers);
        confirmations = List.copyOf(confirmations == null ? List.of() : confirmations);
        rejections = List.copyOf(rejections == null ? List.of() : rejections);
        dataWarnings = List.copyOf(dataWarnings == null ? List.of() : dataWarnings);
    }
    public boolean actionable() { return "APPROVED_SETUP".equals(status); }
    public record PriceZone(BigDecimal lower, BigDecimal upper, String basis) { }
    public record ChartMarker(Instant time, BigDecimal price, String type, String direction, String label) { }
}
