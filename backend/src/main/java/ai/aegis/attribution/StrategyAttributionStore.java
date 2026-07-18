package ai.aegis.attribution;

import ai.aegis.orchestration.DecisionCycleResult;
import ai.aegis.strategy.CandidateSignal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class StrategyAttributionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StrategyAttributionStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(DecisionCycleResult cycle) {
        for (CandidateSignal signal : cycle.candidateSignals()) {
            jdbc.update("""
                    INSERT INTO intelligence.strategy_attribution
                    (id, strategy_id, symbol, interval_name, side, score, eligible,
                     feature_snapshot, confirmations, rejections, observed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?)
                    """,
                    UUID.randomUUID(), signal.strategyId(), signal.symbol(), signal.interval(), signal.side(),
                    signal.score(), signal.eligible(), json(cycle.featureSnapshot().features()),
                    json(signal.confirmations()), json(signal.rejections()), Timestamp.from(signal.createdAt()));
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize strategy attribution", exception);
        }
    }
}
