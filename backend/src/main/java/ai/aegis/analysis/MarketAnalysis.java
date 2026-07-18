package ai.aegis.analysis;

import ai.aegis.risk.RiskPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MarketAnalysis(
        String symbol,
        String interval,
        String decision,
        int score,
        String grade,
        BigDecimal confidence,
        Map<String, BigDecimal> indicators,
        RiskPlan risk,
        List<String> reasons,
        Instant generatedAt
) {
}
