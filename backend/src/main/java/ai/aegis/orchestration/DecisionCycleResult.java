package ai.aegis.orchestration;

import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.strategy.CandidateSignal;

import java.time.Instant;
import java.util.List;

public record DecisionCycleResult(
        String symbol,
        String interval,
        FeatureSnapshot featureSnapshot,
        List<CandidateSignal> candidateSignals,
        String finalDirection,
        int finalScore,
        List<String> reasons,
        Instant completedAt
) { }
