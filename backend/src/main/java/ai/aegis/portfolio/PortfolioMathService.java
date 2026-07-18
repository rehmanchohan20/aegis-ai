package ai.aegis.portfolio;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioMathService {

    public PortfolioAllocation riskParity(Map<String, List<Double>> returnsByAsset) {
        validate(returnsByAsset);
        Map<String, Double> vol = new LinkedHashMap<>();
        returnsByAsset.forEach((asset, values) -> vol.put(asset, stdev(values)));

        double inverseVolSum = vol.values().stream().mapToDouble(v -> 1.0 / Math.max(v, 1e-9)).sum();
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        vol.forEach((asset, value) -> weights.put(asset,
                bd((1.0 / Math.max(value, 1e-9)) / inverseVolSum)));

        double[][] covariance = covarianceMatrix(new ArrayList<>(returnsByAsset.values()));
        double[] w = weights.values().stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double portfolioVariance = quadraticForm(w, covariance);
        double portfolioVolatility = Math.sqrt(Math.max(portfolioVariance, 0));
        double weightedAssetVol = 0;
        int i = 0;
        for (String asset : weights.keySet()) weightedAssetVol += w[i++] * vol.get(asset);
        double diversificationRatio = portfolioVolatility == 0 ? 0 : weightedAssetVol / portfolioVolatility;

        List<String> warnings = new ArrayList<>();
        double maxCorrelation = maximumCorrelation(new ArrayList<>(returnsByAsset.values()));
        if (maxCorrelation > 0.85) warnings.add("Portfolio contains highly correlated assets");
        if (diversificationRatio < 1.10) warnings.add("Portfolio has weak diversification benefit");
        boolean acceptable = maxCorrelation <= 0.95 && diversificationRatio >= 1.02;

        return new PortfolioAllocation(Map.copyOf(weights), bd(portfolioVolatility),
                bd(diversificationRatio), acceptable, List.copyOf(warnings));
    }

    public BigDecimal volatilityTargetWeight(List<Double> returns, BigDecimal targetVolatility, BigDecimal maximumWeight) {
        if (targetVolatility == null || targetVolatility.signum() <= 0 || maximumWeight == null || maximumWeight.signum() <= 0) {
            throw new IllegalArgumentException("positive targetVolatility and maximumWeight are required");
        }
        double realized = stdev(returns);
        if (realized <= 0) return BigDecimal.ZERO;
        return bd(Math.min(maximumWeight.doubleValue(), targetVolatility.doubleValue() / realized));
    }

    private double[][] covarianceMatrix(List<List<Double>> series) {
        int n = series.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) matrix[i][j] = covariance(series.get(i), series.get(j));
        }
        return matrix;
    }

    private double maximumCorrelation(List<List<Double>> series) {
        double max = -1;
        for (int i = 0; i < series.size(); i++) {
            for (int j = i + 1; j < series.size(); j++) {
                max = Math.max(max, correlation(series.get(i), series.get(j)));
            }
        }
        return max;
    }

    private double correlation(List<Double> a, List<Double> b) {
        double denominator = stdev(a) * stdev(b);
        return denominator == 0 ? 0 : covariance(a, b) / denominator;
    }

    private double covariance(List<Double> a, List<Double> b) {
        if (a.size() != b.size() || a.size() < 2) throw new IllegalArgumentException("aligned return series with at least two values are required");
        double meanA = a.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double meanB = b.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double sum = 0;
        for (int i = 0; i < a.size(); i++) sum += (a.get(i) - meanA) * (b.get(i) - meanB);
        return sum / (a.size() - 1);
    }

    private double stdev(List<Double> values) {
        if (values == null || values.size() < 2) throw new IllegalArgumentException("at least two returns are required");
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
        return Math.sqrt(Math.max(variance, 0));
    }

    private double quadraticForm(double[] weights, double[][] covariance) {
        double value = 0;
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights.length; j++) value += weights[i] * covariance[i][j] * weights[j];
        }
        return value;
    }

    private void validate(Map<String, List<Double>> data) {
        if (data == null || data.size() < 2) throw new IllegalArgumentException("at least two assets are required");
        int length = data.values().iterator().next().size();
        if (length < 20 || data.values().stream().anyMatch(v -> v.size() != length)) {
            throw new IllegalArgumentException("aligned return series with at least twenty values are required");
        }
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }
}
