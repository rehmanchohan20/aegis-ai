package ai.aegis.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapValidationServiceTest {
    private final BootstrapValidationService service = new BootstrapValidationService();

    @Test
    void acceptsStablePositiveEdge() {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < 100; i++) returns.add(i % 5 == 0 ? -0.002 : 0.004);
        BootstrapResult result = service.analyze(returns, 2000, 42L);
        assertTrue(result.probabilityMeanPositive().doubleValue() > 0.95);
        assertTrue(result.statisticallyPromising());
    }

    @Test
    void rejectsSmallSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(List.of(0.01, -0.01), 1000, 1L));
    }
}
