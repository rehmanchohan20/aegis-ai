package ai.aegis.validation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class BootstrapValidationService {

    public BootstrapResult analyze(List<Double> tradeReturns, int samples, long seed) {
        if (tradeReturns == null || tradeReturns.size() < 20) {
            throw new IllegalArgumentException("at least twenty trade returns are required");
        }
        if (samples < 500 || samples > 100_000) {
            throw new IllegalArgumentException("samples must be between 500 and 100000");
        }

        Random random = new Random(seed);
        List<Double> bootstrappedMeans = new ArrayList<>(samples);
        int positive = 0;
        for (int sample = 0; sample < samples; sample++) {
            double sum = 0;
            for (int i = 0; i < tradeReturns.size(); i++) {
                sum += tradeReturns.get(random.nextInt(tradeReturns.size()));
            }
            double mean = sum / tradeReturns.size();
            bootstrappedMeans.add(mean);
            if (mean > 0) positive++;
        }
        Collections.sort(bootstrappedMeans);
        double lower = percentile(bootstrappedMeans, 0.025);
        double upper = percentile(bootstrappedMeans, 0.975);
        double probabilityPositive = (double) positive / samples;
        double observed = tradeReturns.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        List<String> warnings = new ArrayList<>();
        if (lower <= 0) warnings.add("95% confidence interval includes zero; edge is not statistically robust");
        if (probabilityPositive < 0.90) warnings.add("Probability of positive expectancy is below 90%");
        if (tradeReturns.size() < 50) warnings.add("Small trade sample increases estimation uncertainty");

        return new BootstrapResult(bd(observed), bd(lower), bd(upper), bd(probabilityPositive),
                lower > 0 && probabilityPositive >= 0.95, List.copyOf(warnings));
    }

    private double percentile(List<Double> values, double p) {
        double index = p * (values.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return values.get(lower);
        double fraction = index - lower;
        return values.get(lower) * (1 - fraction) + values.get(upper) * fraction;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }
}
