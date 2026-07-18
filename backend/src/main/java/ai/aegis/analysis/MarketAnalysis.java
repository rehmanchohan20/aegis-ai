package ai.aegis.analysis;

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
        List<String> reasons,
        Instant generatedAt
) {
}
