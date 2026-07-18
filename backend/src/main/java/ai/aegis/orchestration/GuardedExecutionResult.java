package ai.aegis.orchestration;

import ai.aegis.paper.PaperTrade;
import ai.aegis.risk.RiskPlan;
import ai.aegis.supervisor.SupervisorDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GuardedExecutionResult(
        DecisionCycleResult decisionCycle,
        SupervisorDecision supervisorDecision,
        RiskPlan riskPlan,
        BigDecimal quantity,
        PaperTrade paperTrade,
        String status,
        List<String> reasons,
        Instant completedAt
) {
    public GuardedExecutionResult {
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
}
