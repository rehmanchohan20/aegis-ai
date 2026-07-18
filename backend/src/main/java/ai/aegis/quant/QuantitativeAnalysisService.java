package ai.aegis.quant;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class QuantitativeAnalysisService {
    private static final int SCALE = 8;

    public QuantMetrics analyze(List<Candle> candles) {
        if (candles == null || candles.size() < 31) {
            throw new IllegalArgumentException("At least 31 candles are required for quantitative analysis");
        }

        List<Double> returns = logReturns(candles);
        double mean = mean(returns);
        double volatility = sampleStdDev(returns, mean);
        double downside = downsideDeviation(returns);
        double sharpe = volatility == 0 ? 0 : mean / volatility;
        double sortino = downside == 0 ? 0 : mean / downside;

        List<Double> sorted = returns.stream().sorted(Comparator.naturalOrder()).toList();
        int varIndex = Math.max(0, (int) Math.floor(sorted.size() * 0.05));
        double var95 = -sorted.get(varIndex);
        double cvar95 = -sorted.subList(0, varIndex + 1).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double skewness = skewness(returns, mean, volatility);
        double kurtosis = excessKurtosis(returns, mean, volatility);
        double autocorrelation = lagOneAutocorrelation(returns, mean);
        double latestZScore = volatility == 0 ? 0 : (returns.get(returns.size() - 1) - mean) / volatility;

        List<String> warnings = new ArrayList<>();
        if (returns.size() < 100) warnings.add("Statistical sample is below 100 returns; confidence is limited.");
        if (kurtosis > 3) warnings.add("Return distribution has fat tails; normal-distribution risk estimates may understate losses.");
        if (Math.abs(skewness) > 1) warnings.add("Return distribution is materially skewed.");
        if (Math.abs(autocorrelation) > 0.25) warnings.add("Returns show serial dependence; signals may be regime-sensitive.");
        if (var95 > 0.03) warnings.add("Estimated one-period 95% Value at Risk exceeds 3%.");

        return new QuantMetrics(
                returns.size(), bd(mean), bd(volatility), bd(downside), bd(sharpe), bd(sortino),
                bd(var95), bd(cvar95), bd(skewness), bd(kurtosis), bd(autocorrelation), bd(latestZScore),
                List.copyOf(warnings));
    }

    private List<Double> logReturns(List<Candle> candles) {
        List<Double> values = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            double previous = candles.get(i - 1).close().doubleValue();
            double current = candles.get(i).close().doubleValue();
            if (previous <= 0 || current <= 0) throw new IllegalArgumentException("Candle closes must be positive");
            values.add(Math.log(current / previous));
        }
        return values;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double sampleStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    private double downsideDeviation(List<Double> values) {
        double sum = values.stream().filter(v -> v < 0).mapToDouble(v -> v * v).sum();
        return Math.sqrt(sum / values.size());
    }

    private double skewness(List<Double> values, double mean, double stdDev) {
        if (stdDev == 0) return 0;
        double n = values.size();
        return values.stream().mapToDouble(v -> Math.pow((v - mean) / stdDev, 3)).sum() / n;
    }

    private double excessKurtosis(List<Double> values, double mean, double stdDev) {
        if (stdDev == 0) return 0;
        double n = values.size();
        return values.stream().mapToDouble(v -> Math.pow((v - mean) / stdDev, 4)).sum() / n - 3;
    }

    private double lagOneAutocorrelation(List<Double> values, double mean) {
        if (values.size() < 3) return 0;
        double numerator = 0;
        double denominator = 0;
        for (int i = 1; i < values.size(); i++) numerator += (values.get(i) - mean) * (values.get(i - 1) - mean);
        for (double value : values) denominator += Math.pow(value - mean, 2);
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private BigDecimal bd(double value) {
        if (!Double.isFinite(value)) value = 0;
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
