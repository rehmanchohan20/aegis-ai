package ai.aegis.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentApprovalServiceTest {
    @Test
    void refusesPromotionWhenModelQualityIsBelowMinimums() {
        UUID id = UUID.randomUUID();
        ModelRegistryService registry = mock(ModelRegistryService.class);
        when(registry.get(id)).thenReturn(version(id, Map.of("balancedAccuracy", .40, "macroF1", .35,
                "directionalPrecision", .42, "validationSampleCount", 50, "calibrationError", .30)));
        DeploymentApprovalService service = service(registry);
        assertFalse(service.evaluate(id, new BigDecimal(".8"), new BigDecimal("1.2"),
                new BigDecimal(".1")).approved());
    }

    @Test
    void approvesOnlyWhenResearchAndTradingMinimumsPass() {
        UUID id = UUID.randomUUID();
        ModelRegistryService registry = mock(ModelRegistryService.class);
        when(registry.get(id)).thenReturn(version(id, Map.of("balancedAccuracy", .62, "macroF1", .58,
                "directionalPrecision", .60, "validationSampleCount", 300, "calibrationError", .08)));
        DeploymentApprovalService service = service(registry);
        assertTrue(service.evaluate(id, new BigDecimal(".8"), new BigDecimal("1.2"),
                new BigDecimal(".1")).approved());
    }

    private DeploymentApprovalService service(ModelRegistryService registry) {
        return new DeploymentApprovalService(mock(JdbcTemplate.class), new ObjectMapper(), registry,
                new BigDecimal(".55"), new BigDecimal(".50"), new BigDecimal(".52"), 150,
                new BigDecimal(".15"), new BigDecimal(".40"));
    }

    private ModelRegistryService.ModelVersion version(UUID id, Map<String, Object> metrics) {
        Map<String, Object> completeMetrics = new java.util.HashMap<>(metrics);
        completeMetrics.putIfAbsent("worstFoldBalancedAccuracy", .55);
        return new ModelRegistryService.ModelVersion(id, "aegis-direction", "v1", "CANDIDATE",
                completeMetrics, "model.joblib", List.of("signal"), Instant.now(), null);
    }
}
