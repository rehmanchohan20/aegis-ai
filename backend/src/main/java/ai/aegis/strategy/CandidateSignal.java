package ai.aegis.strategy;

import java.time.Instant;
import java.util.List;

public record CandidateSignal(
        String strategyId,
        String symbol,
        String interval,
        String side,
        int score,
        boolean eligible,
        List<String> confirmations,
        List<String> rejections,
        Instant createdAt
) {
    public CandidateSignal {
        confirmations = List.copyOf(confirmations == null ? List.of() : confirmations);
        rejections = List.copyOf(rejections == null ? List.of() : rejections);
    }
}
