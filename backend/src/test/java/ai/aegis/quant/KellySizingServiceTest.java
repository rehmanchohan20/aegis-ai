package ai.aegis.quant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KellySizingServiceTest {
    private final KellySizingService service = new KellySizingService();

    @Test
    void shouldCapAggressiveKellySizingAtTwoPercent() {
        KellySizingResult result = service.calculate(
                BigDecimal.valueOf(10000), BigDecimal.valueOf(0.60),
                BigDecimal.valueOf(2), BigDecimal.ONE);

        assertThat(result.cappedRiskFraction()).isEqualByComparingTo("0.020000");
        assertThat(result.recommendedRiskAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRecommendZeroRiskWhenEstimatedEdgeIsNegative() {
        KellySizingResult result = service.calculate(
                BigDecimal.valueOf(10000), BigDecimal.valueOf(0.30),
                BigDecimal.ONE, BigDecimal.ONE);

        assertThat(result.cappedRiskFraction()).isZero();
        assertThat(result.warnings()).anyMatch(value -> value.contains("non-positive"));
    }
}
