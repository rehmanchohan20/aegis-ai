package ai.aegis.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;

@Service
public class ModelRegistryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MlModelDeploymentClient deploymentClient;

    public ModelRegistryService(JdbcTemplate jdbc, ObjectMapper mapper, MlModelDeploymentClient deploymentClient) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.deploymentClient = deploymentClient;
    }

    public ModelVersion register(String modelName, String version, Map<String, Object> metrics,
                                 String artifactUri, List<String> featureNames) {
        if (modelName == null || modelName.isBlank() || version == null || version.isBlank())
            throw new IllegalArgumentException("modelName and version are required");
        if (artifactUri == null || artifactUri.isBlank()) throw new IllegalArgumentException("artifactUri is required");
        if (featureNames == null || featureNames.isEmpty() || featureNames.stream().anyMatch(name -> name == null || name.isBlank()))
            throw new IllegalArgumentException("a non-empty feature schema is required");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO intelligence.model_registry
                (id, model_name, version, status, metrics, artifact_uri, feature_names, created_at)
                VALUES (?, ?, ?, 'CANDIDATE', ?::jsonb, ?, ?::jsonb, ?)
                """, id, modelName, version, json(metrics == null ? Map.of() : metrics), artifactUri,
                json(featureNames == null ? List.of() : featureNames), Timestamp.from(Instant.now()));
        return get(id);
    }

    @Transactional
    public ModelVersion activate(UUID id) {
        return activate(id, "ACTIVATE");
    }

    private ModelVersion activate(UUID id, String action) {
        ModelVersion candidate = get(id);
        if (!approved(id)) throw new IllegalStateException("Model has no passing deployment approval");
        verifyArtifact(candidate.artifactUri());
        deploymentClient.activate(candidate.modelName(), candidate.version());
        ModelVersion previous = active(candidate.modelName());
        jdbc.update("UPDATE intelligence.model_registry SET status='RETIRED', retired_at=? WHERE model_name=? AND status='ACTIVE'",
                Timestamp.from(Instant.now()), candidate.modelName());
        jdbc.update("UPDATE intelligence.model_registry SET status='ACTIVE', activated_at=?, retired_at=NULL WHERE id=?",
                Timestamp.from(Instant.now()), id);
        jdbc.update("""
                INSERT INTO intelligence.model_activation_audit
                (id, model_id, model_name, version, action, previous_model_id, performed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), candidate.id(), candidate.modelName(), candidate.version(), action,
                previous == null ? null : previous.id(), Timestamp.from(Instant.now()));
        return get(id);
    }

    @Transactional
    public ModelVersion rollback(String modelName, String version) {
        List<UUID> ids = jdbc.query("SELECT id FROM intelligence.model_registry WHERE model_name=? AND version=?",
                (rs, n) -> UUID.fromString(rs.getString("id")), modelName, version);
        if (ids.isEmpty()) throw new IllegalArgumentException("Model version not found");
        return activate(ids.getFirst(), "ROLLBACK");
    }

    public ModelVersion active(String modelName) {
        List<ModelVersion> rows = jdbc.query("SELECT * FROM intelligence.model_registry WHERE model_name=? AND status='ACTIVE'",
                (rs, n) -> map(rs), modelName);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<ModelVersion> list(String modelName) {
        return jdbc.query("SELECT * FROM intelligence.model_registry WHERE model_name=? ORDER BY created_at DESC",
                (rs, n) -> map(rs), modelName);
    }

    public ModelVersion get(UUID id) {
        List<ModelVersion> rows = jdbc.query("SELECT * FROM intelligence.model_registry WHERE id=?",
                (rs, n) -> map(rs), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Model version not found");
        return rows.getFirst();
    }

    private boolean approved(UUID id) {
        List<Boolean> values = jdbc.query("""
                SELECT approved FROM intelligence.deployment_approval
                WHERE model_id=? ORDER BY evaluated_at DESC LIMIT 1
                """, (rs, n) -> rs.getBoolean("approved"), id);
        return !values.isEmpty() && Boolean.TRUE.equals(values.getFirst());
    }

    private void verifyArtifact(String artifactUri) {
        try {
            URI uri = URI.create(artifactUri);
            Path path = uri.getScheme() == null ? Path.of(artifactUri)
                    : "file".equalsIgnoreCase(uri.getScheme()) ? Path.of(uri) : null;
            if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalStateException("Model artifact is not a readable local file: " + artifactUri);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Model artifact URI is invalid: " + artifactUri, invalid);
        }
    }

    @SuppressWarnings("unchecked")
    private ModelVersion map(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new ModelVersion(UUID.fromString(rs.getString("id")), rs.getString("model_name"),
                    rs.getString("version"), rs.getString("status"),
                    mapper.readValue(rs.getString("metrics"), Map.class), rs.getString("artifact_uri"),
                    mapper.readValue(rs.getString("feature_names"), List.class),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toInstant());
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize model metadata", e); }
    }

    public record ModelVersion(UUID id, String modelName, String version, String status,
                               Map<String, Object> metrics, String artifactUri, List<String> featureNames,
                               Instant createdAt, Instant activatedAt) { }
}
