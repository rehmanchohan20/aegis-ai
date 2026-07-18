package ai.aegis.supervisor;

import ai.aegis.feature.FeatureQuality;
import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.feature.FeatureValue;
import ai.aegis.strategy.CandidateSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SupervisorEngineTest {
    private final SupervisorEngine engine = new SupervisorEngine();

    @Test
    void approvesHealthyConsistentSignal() {
        Instant now = Instant.now();
        FeatureSnapshot snapshot = new FeatureSnapshot("BTCUSDT", "5m", now, Map.of(
                "rsi", new FeatureValue("rsi", new BigDecimal("60"), now, FeatureQuality.GOOD, "test")
        ));
        CandidateSignal signal = new CandidateSignal("trend", "BTCUSDT", "5m", "LONG", 84,
                true, List.of("trend confirmed"), List.of(), now);

        SupervisorDecision result = engine.decide(snapshot, List.of(signal), true, true, true);

        assertEquals("APPROVED", result.status());
        assertEquals("LONG", result.side());
    }

    @Test
    void vetoesConflictingSignals() {
        Instant now = Instant.now();
        FeatureSnapshot snapshot = new FeatureSnapshot("BTCUSDT", "5m", now, Map.of());
        CandidateSignal longSignal = new CandidateSignal("trend", "BTCUSDT", "5m", "LONG", 85,
                true, List.of(), List.of(), now);
        CandidateSignal shortSignal = new CandidateSignal("mean-reversion", "BTCUSDT", "5m", "SHORT", 82,
                true, List.of(), List.of(), now);

        SupervisorDecision result = engine.decide(snapshot, List.of(longSignal, shortSignal), true, true, true);

        assertEquals("VETOED", result.status());
        assertEquals("WAIT", result.side());
        assertTrue(result.vetoes().stream().anyMatch(reason -> reason.contains("Conflicting")));
    }
}
