package ai.aegis.ml;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MlExampleLabelingServiceTest {
    @Test
    void volatilityAdjustedThresholdNeverDropsBelowConfiguredFloor() {
        assertEquals(new BigDecimal("0.001"), MlExampleLabelingService.adaptiveThreshold(
                new BigDecimal("0.001"), new BigDecimal("0.0005"), new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.0020"), MlExampleLabelingService.adaptiveThreshold(
                new BigDecimal("0.001"), new BigDecimal("0.004"), new BigDecimal("0.5")));
    }
}
