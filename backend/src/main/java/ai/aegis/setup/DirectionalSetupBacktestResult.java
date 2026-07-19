package ai.aegis.setup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectionalSetupBacktestResult(
        UUID id, String symbol, String timeframe, Instant sampleStart, Instant sampleEnd,
        int evaluatedBars, int totalSetups, int wins, int losses, int ambiguousSameBar,
        BigDecimal winRate, BigDecimal averageR, BigDecimal expectancyR,
        BigDecimal profitFactor, BigDecimal maximumDrawdownR,
        List<Breakdown> resultsByPairTimeframeRegime, List<Trade> trades,
        List<String> warnings, Instant completedAt
) {
    public DirectionalSetupBacktestResult {
        resultsByPairTimeframeRegime = List.copyOf(resultsByPairTimeframeRegime == null ? List.of() : resultsByPairTimeframeRegime);
        trades = List.copyOf(trades == null ? List.of() : trades);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
    public record Breakdown(String symbol, String timeframe, String regime, int trades,
                            BigDecimal winRate, BigDecimal averageR, BigDecimal expectancyR,
                            BigDecimal profitFactor, BigDecimal maximumDrawdownR) { }
    public record Trade(String symbol, String timeframe, String regime, String direction, String setupType,
                        Instant signalTime, Instant entryTime, Instant exitTime, BigDecimal entry,
                        BigDecimal stop, BigDecimal target, BigDecimal realizedR, String exitReason,
                        BigDecimal qualityScore, BigDecimal estimatedTargetProbability) { }
}
