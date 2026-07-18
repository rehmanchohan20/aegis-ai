package ai.aegis.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategyHealthServiceTest {
    private final StrategyHealthService service = new StrategyHealthService();

    @Test
    void haltsWhenDrawdownOrLossStreakBreachesLimits() {
        StrategyHealthDecision result = service.evaluate(
                0.01, -0.003, 0.02, 0.05, 0.25, 0.20, 5, 30, 40);
        assertEquals("HALTED", result.status());
        assertFalse(result.tradingAllowed());
    }

    @Test
    void allowsHealthyLivePerformance() {
        StrategyHealthDecision result = service.evaluate(
                0.01, 0.008, 0.02, 0.022, 0.04, 0.20, 1, 30, 80);
        assertEquals("HEALTHY", result.status());
        assertTrue(result.tradingAllowed());
    }
}
