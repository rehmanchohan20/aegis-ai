package ai.aegis.admission;

import ai.aegis.analysis.MarketAnalysis;
import ai.aegis.market.Candle;

import java.math.BigDecimal;
import java.util.List;

public record TradeAdmissionRequest(
        MarketAnalysis analysis,
        List<Candle> candles,
        BigDecimal accountBalance,
        BigDecimal quantity,
        BigDecimal feeBps,
        BigDecimal slippageBps,
        int alignedTimeframes,
        int openPositions,
        BigDecimal dailyLossPercent
) {
}
