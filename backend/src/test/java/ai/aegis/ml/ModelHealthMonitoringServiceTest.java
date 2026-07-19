package ai.aegis.ml;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelHealthMonitoringServiceTest {
    @Test
    void classifiesHealthTransitions() {
        assertEquals("WATCH", assessment(10, ".70", ".70", ".05", ".5", 0).status());
        assertEquals("HEALTHY", assessment(100, ".65", ".65", ".05", ".5", 0).status());
        assertEquals("DEGRADED", assessment(100, ".50", ".55", ".10", "1.5", 0).status());
        assertEquals("HALTED", assessment(100, ".35", ".60", ".10", ".5", 0).status());
    }

    private ModelHealthMonitoringService.HealthAssessment assessment(int samples, String accuracy,
            String precision, String calibration, String drift, int unresolved) {
        return ModelHealthMonitoringService.assess(samples, new BigDecimal(accuracy), new BigDecimal(precision),
                new BigDecimal(calibration), new BigDecimal(drift), BigDecimal.ZERO, BigDecimal.ZERO,
                unresolved, 30);
    }

    @Test
    void detectsClassAndPopulationDistributionShift() {
        assertEquals(new BigDecimal("0.5"), ModelHealthMonitoringService.distributionShift(
                new long[]{0, 50, 50}, new long[]{50, 50, 0}));
        assertTrue(ModelHealthMonitoringService.populationStabilityIndex(
                new double[]{3, 3, 4, 5}, new double[]{0.2, 0.4, 0.5, 0.8}).doubleValue() > 1.0);
    }
}
