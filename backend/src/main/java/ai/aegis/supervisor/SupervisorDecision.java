package ai.aegis.supervisor;

import java.time.Instant;
import java.util.List;

public record SupervisorDecision(
        String status,
        String side,
        int approvedScore,
        List<String> approvals,
        List<String> vetoes,
        Instant decidedAt
) {
    public SupervisorDecision {
        approvals = List.copyOf(approvals == null ? List.of() : approvals);
        vetoes = List.copyOf(vetoes == null ? List.of() : vetoes);
    }
}
