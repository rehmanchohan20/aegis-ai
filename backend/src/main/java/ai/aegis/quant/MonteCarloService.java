package ai.aegis.quant;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class MonteCarloService {
    public MonteCarloResult simulate(BigDecimal initialBalance, List<BigDecimal> historicalReturns,
                                     int tradesPerSimulation, int simulations, long seed) {
        if (initialBalance == null || initialBalance.signum() <= 0) throw new IllegalArgumentException("initialBalance must be positive");
        if (historicalReturns == null || historicalReturns.size() < 20) throw new IllegalArgumentException("At least 20 historical returns are required");
        if (tradesPerSimulation < 1 || simulations < 100) throw new IllegalArgumentException("tradesPerSimulation must be positive and simulations at least 100");

        Random random = new Random(seed);
        List<BigDecimal> endings = new ArrayList<>();
        List<BigDecimal> drawdowns = new ArrayList<>();
        int losses = 0;
        int ruins = 0;
        BigDecimal ruinLevel = initialBalance.multiply(BigDecimal.valueOf(0.50));

        for (int s = 0; s < simulations; s++) {
            BigDecimal equity = initialBalance;
            BigDecimal peak = initialBalance;
            BigDecimal maxDrawdown = BigDecimal.ZERO;
            for (int t = 0; t < tradesPerSimulation; t++) {
                BigDecimal sampled = historicalReturns.get(random.nextInt(historicalReturns.size()));
                equity = equity.multiply(BigDecimal.ONE.add(sampled));
                if (equity.compareTo(BigDecimal.ZERO) < 0) equity = BigDecimal.ZERO;
                peak = peak.max(equity);
                if (peak.signum() > 0) {
                    BigDecimal dd = peak.subtract(equity).divide(peak, 10, RoundingMode.HALF_UP);
                    maxDrawdown = maxDrawdown.max(dd);
                }
            }
            endings.add(equity);
            drawdowns.add(maxDrawdown);
            if (equity.compareTo(initialBalance) < 0) losses++;
            if (equity.compareTo(ruinLevel) <= 0) ruins++;
        }

        Collections.sort(endings);
        Collections.sort(drawdowns);
        return new MonteCarloResult(simulations, tradesPerSimulation,
                percentile(endings, 0.50), percentile(endings, 0.05), percentile(endings, 0.95),
                ratio(losses, simulations), ratio(ruins, simulations),
                percentile(drawdowns, 0.50), percentile(drawdowns, 0.95));
    }

    private BigDecimal percentile(List<BigDecimal> values, double p) {
        int index = Math.min(values.size() - 1, Math.max(0, (int) Math.floor((values.size() - 1) * p)));
        return values.get(index).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
