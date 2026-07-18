package ai.aegis.admission;

import ai.aegis.execution.ExecutionEstimate;
import ai.aegis.regime.MarketRegime;

import java.math.BigDecimal;
import java.util.List;

public record TradeAdmissionDecision(
        boolean approved,
        String decision,
        int qualityScore,
        String qualityGrade,
        BigDecimal maximumPositionNotional,
        MarketRegime regime,
        ExecutionEstimate execution,
        List<String> blockers,
        List<String> confirmations
) {
}
