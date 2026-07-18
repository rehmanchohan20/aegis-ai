package ai.aegis.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRiskServiceTest {
    private final PortfolioRiskService service = new PortfolioRiskService();

    @Test
    void shouldApproveConservativeTradeWithinLimits() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.valueOf(50), 1, 0,
                Instant.parse("2026-01-01T00:00:00Z"));

        RiskDecision result = service.evaluate(snapshot, RiskPolicy.conservative(),
                BigDecimal.ONE, BigDecimal.valueOf(100), Duration.ofMinutes(1),
                Instant.parse("2026-01-01T00:10:00Z"));

        assertThat(result.approved()).isTrue();
        assertThat(result.allowedRiskAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectAfterDailyLossLimit() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(-35), BigDecimal.ZERO, 0, 0, null);

        RiskDecision result = service.evaluate(snapshot, RiskPolicy.conservative(),
                BigDecimal.ONE, BigDecimal.valueOf(100), Duration.ofMinutes(1), Instant.now());

        assertThat(result.approved()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("Daily loss"));
    }
}
