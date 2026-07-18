package ai.aegis.orchestration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuardedExecutionRequestTest {

    @Test
    void acceptsControlledRiskAtTwoPercentOrLess() {
        GuardedExecutionRequest request = new GuardedExecutionRequest(List.of(),
                BigDecimal.valueOf(10_000), BigDecimal.ONE,
                true, true, true, false);

        assertEquals(BigDecimal.ONE, request.riskPercent());
    }

    @Test
    void rejectsRiskAboveTwoPercent() {
        assertThrows(IllegalArgumentException.class, () -> new GuardedExecutionRequest(List.of(),
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(2.01),
                true, true, true, false));
    }

    @Test
    void rejectsNonPositiveBalance() {
        assertThrows(IllegalArgumentException.class, () -> new GuardedExecutionRequest(List.of(),
                BigDecimal.ZERO, BigDecimal.ONE,
                true, true, true, false));
    }
}
