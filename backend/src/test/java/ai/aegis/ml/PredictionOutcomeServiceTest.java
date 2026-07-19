package ai.aegis.ml;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredictionOutcomeServiceTest {
    @Test
    void resolvesLongAndCorrectness() {
        var result = PredictionOutcomeService.resolve(new BigDecimal("100"), new BigDecimal("101"),
                "LONG", new BigDecimal("0.001"));
        assertEquals(1, result.outcomeLabel());
        assertTrue(result.correct());
        assertEquals(new BigDecimal("0.010000000000"), result.forwardReturn());
    }

    @Test
    void resolvesNeutralZoneAsWait() {
        var result = PredictionOutcomeService.resolve(new BigDecimal("100"), new BigDecimal("100.05"),
                "WAIT", new BigDecimal("0.001"));
        assertEquals(0, result.outcomeLabel());
        assertTrue(result.correct());
    }

    @Test
    void marksWrongDirectionIncorrect() {
        var result = PredictionOutcomeService.resolve(new BigDecimal("100"), new BigDecimal("98"),
                "LONG", new BigDecimal("0.001"));
        assertEquals(-1, result.outcomeLabel());
        assertFalse(result.correct());
    }

    @Test
    void resolvesTakeProfitBeforeStopAcrossChronologicalCandles() {
        var result = PredictionOutcomeService.resolvePath(List.of(
                candle("100", "101", "99"), candle("104", "106", "103"), candle("95", "97", "94")),
                new BigDecimal("95"), new BigDecimal("105"));
        assertEquals("TAKE_PROFIT_FIRST", result.status());
        assertEquals(Boolean.TRUE, result.takeProfitFirst());
        assertEquals(Boolean.FALSE, result.stopLossFirst());
    }

    @Test
    void doesNotInventIntracandleOrderingWhenBothLevelsHit() {
        var result = PredictionOutcomeService.resolvePath(List.of(candle("100", "106", "94")),
                new BigDecimal("95"), new BigDecimal("105"));
        assertEquals("AMBIGUOUS_SAME_CANDLE", result.status());
        assertNull(result.takeProfitFirst());
        assertNull(result.stopLossFirst());
    }

    private static PredictionOutcomeService.ClosingPrice candle(String close, String high, String low) {
        return new PredictionOutcomeService.ClosingPrice(new BigDecimal(close), new BigDecimal(high),
                new BigDecimal(low), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
