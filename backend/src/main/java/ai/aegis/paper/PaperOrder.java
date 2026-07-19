package ai.aegis.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperOrder(UUID id, String clientOrderId, UUID tradeId, String symbol, String interval,
                         String side, String orderType, String status, BigDecimal requestedQuantity,
                         BigDecimal filledQuantity, BigDecimal limitPrice, BigDecimal stopPrice,
                         BigDecimal averageFillPrice, BigDecimal slippage, BigDecimal fee,
                         String rejectionReason, Instant submittedAt, Instant firstFillAt,
                         Instant completedAt, Instant expiresAt, Instant updatedAt) { }
