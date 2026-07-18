package ai.aegis.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeploymentApprovalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DeploymentApprovalService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Approval evaluate(UUID modelId, BigDecimal efficiency, BigDecimal sharpe, BigDecimal maxDrawdown) {
        List<String> reasons = new ArrayList<>();
        if (efficiency == null || efficiency.compareTo(new BigDecimal("0.60")) < 0)
            reasons.add("Walk-forward efficiency is below 0.60");
        if (sharpe == null || sharpe.compareTo(new BigDecimal("0.75")) < 0)
            reasons.add("Out-of-sample Sharpe is below 0.75");
        if (maxDrawdown == null || maxDrawdown.compareTo(new BigDecimal("0.15")) > 0)
            reasons.add("Maximum drawdown exceeds 15%");
        boolean approved = reasons.isEmpty();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO intelligence.deployment_approval
                (id, model_id, walk_forward_efficiency, out_of_sample_sharpe, maximum_drawdown, approved, reasons, evaluated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, modelId, efficiency, sharpe, maxDrawdown, approved, json(reasons), Timestamp.from(Instant.now()));
        return new Approval(id, modelId, approved, List.copyOf(reasons), Instant.now());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public record Approval(UUID id, UUID modelId, boolean approved, List<String> reasons, Instant evaluatedAt) { }
}
