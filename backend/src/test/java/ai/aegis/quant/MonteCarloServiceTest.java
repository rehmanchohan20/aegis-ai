package ai.aegis.quant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MonteCarloServiceTest {
    private final MonteCarloService service = new MonteCarloService();

    @Test
    void shouldProduceDeterministicSimulationForFixedSeed() {
        List<BigDecimal> returns = IntStream.range(0, 40)
                .mapToObj(i -> i % 3 == 0 ? BigDecimal.valueOf(-0.01) : BigDecimal.valueOf(0.012))
                .toList();

        MonteCarloResult first = service.simulate(BigDecimal.valueOf(1000), returns, 50, 500, 42L);
        MonteCarloResult second = service.simulate(BigDecimal.valueOf(1000), returns, 50, 500, 42L);

        assertThat(first).isEqualTo(second);
        assertThat(first.probabilityOfRuin()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(first.percentile95MaxDrawdown()).isGreaterThanOrEqualTo(first.medianMaxDrawdown());
    }
}
