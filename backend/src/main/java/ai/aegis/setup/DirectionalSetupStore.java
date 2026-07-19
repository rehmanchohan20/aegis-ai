package ai.aegis.setup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class DirectionalSetupStore {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public DirectionalSetupStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void save(DirectionalSetup setup) {
        jdbc.update("""
                INSERT INTO intelligence.directional_setup_snapshot
                    (id, symbol, interval_name, evaluated_at, status, direction, setup_type,
                     trade_quality_score, expected_r_multiple, target_hit_probability,
                     stop_hit_probability, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, setup.id(), setup.symbol(), setup.timeframe(), Timestamp.from(setup.evaluatedAt()),
                setup.status(), setup.direction(), setup.setupType(), setup.tradeQualityScore(),
                setup.expectedRMultiple(), setup.takeProfitHitFirstProbability(),
                setup.stopHitFirstProbability(), json(setup));
    }

    public DirectionalSetup latest(String symbol, String timeframe) {
        List<DirectionalSetup> rows = jdbc.query("""
                SELECT payload FROM intelligence.directional_setup_snapshot
                WHERE symbol=? AND interval_name=? ORDER BY evaluated_at DESC LIMIT 1
                """, (rs, row) -> read(rs.getString("payload")), symbol.toUpperCase(), timeframe);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String json(Object value) { try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize directional setup", e); } }
    private DirectionalSetup read(String value) { try { return mapper.readValue(value, DirectionalSetup.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored directional setup", e); } }
}
