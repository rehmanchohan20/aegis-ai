package ai.aegis.journal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        Instant closedAt
) {
}
