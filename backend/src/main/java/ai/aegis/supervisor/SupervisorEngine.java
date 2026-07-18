package ai.aegis.supervisor;

import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.strategy.CandidateSignal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SupervisorEngine {
    private static final Duration MAX_FEATURE_AGE = Duration.ofMinutes(5);

    public SupervisorDecision decide(FeatureSnapshot snapshot,
                                     List<CandidateSignal> candidates,
                                     boolean riskApproved,
                                     boolean strategyHealthy,
                                     boolean executionHealthy) {
        List<String> approvals = new ArrayList<>();
        List<String> vetoes = new ArrayList<>();

        if (Duration.between(snapshot.generatedAt(), Instant.now()).compareTo(MAX_FEATURE_AGE) > 0) {
            vetoes.add("Feature snapshot is stale");
        }
        if (!snapshot.unusableFeatures().isEmpty()) {
            vetoes.add("Unusable features present: " + String.join(", ", snapshot.unusableFeatures()));
        }
        if (!riskApproved) vetoes.add("Risk engine rejected trade");
        else approvals.add("Risk engine approved");
        if (!strategyHealthy) vetoes.add("Strategy health gate rejected trade");
        else approvals.add("Strategy health is acceptable");
        if (!executionHealthy) vetoes.add("Execution venue or cost gate rejected trade");
        else approvals.add("Execution gate approved");

        List<CandidateSignal> eligible = candidates == null ? List.of() : candidates.stream()
                .filter(CandidateSignal::eligible)
                .toList();
        if (eligible.isEmpty()) {
            vetoes.add("No eligible strategy signal");
        }

        long longCount = eligible.stream().filter(signal -> "LONG".equals(signal.side())).count();
        long shortCount = eligible.stream().filter(signal -> "SHORT".equals(signal.side())).count();
        if (longCount > 0 && shortCount > 0) {
            vetoes.add("Conflicting LONG and SHORT strategies");
        }

        String side = longCount > shortCount ? "LONG" : shortCount > longCount ? "SHORT" : "WAIT";
        int score = eligible.stream()
                .filter(signal -> signal.side().equals(side))
                .mapToInt(CandidateSignal::score)
                .max().orElse(0);

        if (score < 70) {
            vetoes.add("Best strategy score below supervisor threshold");
        } else {
            approvals.add("Strategy score passed supervisor threshold");
        }

        return new SupervisorDecision(vetoes.isEmpty() ? "APPROVED" : "VETOED",
                vetoes.isEmpty() ? side : "WAIT", score, approvals, vetoes, Instant.now());
    }
}
