package ai.aegis.ml;

import ai.aegis.feature.FeatureSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PredictionAuditServiceTest {
    @Test
    void persistsStartingPriceAndThreeClassProbabilities() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PredictionAuditService service = new PredictionAuditService(jdbc, new ObjectMapper());
        MlPrediction prediction = new MlPrediction(new BigDecimal(".7"), new BigDecimal(".2"),
                new BigDecimal(".1"), "LONG", new BigDecimal(".7"), "aegis-direction:v1",
                new BigDecimal(".5"), new BigDecimal(".2"), "HEALTHY");
        FeatureSnapshot snapshot = new FeatureSnapshot("BTCUSDT", "1m", Instant.now(), Map.of());

        assertNotNull(service.record("BTCUSDT", "1m", "trend", "LONG", prediction, snapshot,
                new BigDecimal("100")));
        verify(jdbc).update(contains("starting_price"), any(Object[].class));
    }

    @Test
    void summaryTypesNullableModelVersionForPostgresql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(any(String.class), any(Object[].class))).thenReturn(Map.of());
        PredictionAuditService service = new PredictionAuditService(jdbc, new ObjectMapper());

        service.summary(null);

        verify(jdbc).queryForMap(contains("CAST(? AS TEXT) IS NULL"), any(Object[].class));
        verify(jdbc).queryForMap(contains("SELECT expected_calibration_error FROM calibration"),
                any(Object[].class));
    }
}
