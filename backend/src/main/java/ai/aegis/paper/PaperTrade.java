package ai.aegis.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperTrade(
        UUID id,
        String symbol,
        String interval,
        String side,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal quantity,
        String status,
        BigDecimal realizedPnl,
        Instant openedAt,
        Instant closedAt
) {
}
