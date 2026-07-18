package ai.aegis.monitoring;

import ai.aegis.paper.PaperTrade;

import java.time.Instant;
import java.util.List;

public record PaperMonitoringResult(
        int inspected,
        int closed,
        List<PaperTrade> updatedTrades,
        List<String> warnings,
        Instant completedAt
) {
    public PaperMonitoringResult {
        updatedTrades = List.copyOf(updatedTrades == null ? List.of() : updatedTrades);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
