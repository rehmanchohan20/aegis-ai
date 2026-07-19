package ai.aegis.journal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import ai.aegis.risk.RiskPlan;

public record TradeJournalEntry(
        UUID id,
        String symbol,
        String interval,
        String side,
        String status,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal quantity,
        BigDecimal realizedPnl,
        Integer signalScore,
        String signalGrade,
        String rationale,
        Instant openedAt,
        Instant closedAt,
        String strategyId,
        String modelVersion,
        String rulesDirection,
        String mlDirection,
        Map<String, BigDecimal> mlProbabilities,
        UUID featureSnapshotRef,
        RiskPlan riskPlan,
        String closureReason,
        String marketRegime
) {
    public TradeJournalEntry {
        mlProbabilities = Map.copyOf(mlProbabilities == null ? Map.of() : mlProbabilities);
    }

    public TradeJournalEntry(UUID id, String symbol, String interval, String side, String status,
                             BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal takeProfit,
                             BigDecimal quantity, BigDecimal realizedPnl, Integer signalScore,
                             String signalGrade, String rationale, Instant openedAt, Instant closedAt) {
        this(id, symbol, interval, side, status, entryPrice, stopLoss, takeProfit, quantity,
                realizedPnl, signalScore, signalGrade, rationale, openedAt, closedAt,
                null, null, null, null, Map.of(), null, null, null, null);
    }
}
