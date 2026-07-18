package ai.aegis.execution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionCostServiceTest {
    private final ExecutionCostService service = new ExecutionCostService();

    @Test
    void shouldApplyAdverseSlippageForLongEntry() {
        ExecutionEstimate estimate = service.estimate("LONG", BigDecimal.valueOf(100),
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(5));

        assertThat(estimate.expectedFillPrice()).isGreaterThan(estimate.requestedPrice());
        assertThat(estimate.totalCost()).isPositive();
        assertThat(estimate.breakEvenMovePercent()).isPositive();
    }

    @Test
    void shouldApplyAdverseSlippageForShortEntry() {
        ExecutionEstimate estimate = service.estimate("SHORT", BigDecimal.valueOf(100),
                BigDecimal.ONE, BigDecimal.valueOf(10), BigDecimal.valueOf(5));

        assertThat(estimate.expectedFillPrice()).isLessThan(estimate.requestedPrice());
    }
}
