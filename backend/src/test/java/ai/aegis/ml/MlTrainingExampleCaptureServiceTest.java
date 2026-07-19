package ai.aegis.ml;

import ai.aegis.feature.FeatureQuality;
import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.feature.FeatureValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MlTrainingExampleCaptureServiceTest {
    @Test
    void persistsOnlyCompleteChronologicalFeatureVectors() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new MlTrainingExampleCaptureService(jdbc, new ObjectMapper());
        Instant time = Instant.parse("2026-01-01T00:01:00Z");
        var snapshot = new FeatureSnapshot("BTCUSDT", "1m", time, Map.of("momentum",
                new FeatureValue("momentum", BigDecimal.ONE, time, FeatureQuality.GOOD, "test")));
        assertTrue(service.capture(snapshot, "trend", time));
    }

    @Test
    void refusesIncompleteVectorsInsteadOfChangingTrainingSchema() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new MlTrainingExampleCaptureService(jdbc, new ObjectMapper());
        Instant time = Instant.parse("2026-01-01T00:01:00Z");
        var snapshot = new FeatureSnapshot("BTCUSDT", "1m", time, Map.of("microstructure",
                new FeatureValue("microstructure", null, time, FeatureQuality.MISSING, "stream")));
        assertFalse(service.capture(snapshot, null, time));
        verifyNoInteractions(jdbc);
    }
}
