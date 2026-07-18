package ai.aegis.analysis;

import java.util.List;
import java.util.Map;

public record MultiTimeframeDecision(
        String symbol,
        String decision,
        int alignmentScore,
        boolean approved,
        Map<String, String> timeframeDecisions,
        List<String> reasons
) {
}
