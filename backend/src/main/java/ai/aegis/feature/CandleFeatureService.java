package ai.aegis.feature;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandleFeatureService {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public FeatureSnapshot build(List<Candle> input) {
        if (input == null || input.size() < 30) {
            throw new IllegalArgumentException("at least 30 candles are required");
        }
        List<Candle> candles = input.stream().filter(Candle::closed).toList();
        if (candles.size() < 30) throw new IllegalArgumentException("at least 30 closed candles are required");

        Candle latest = candles.get(candles.size() - 1);
        Map<String, FeatureValue> features = new LinkedHashMap<>();
        add(features, "return1", pct(candles.get(candles.size()-2).close(), latest.close()), latest);
        add(features, "return5", pct(candles.get(candles.size()-6).close(), latest.close()), latest);
        add(features, "momentum10", pct(candles.get(candles.size()-11).close(), latest.close()), latest);
        add(features, "volatility20", volatility(candles, 20), latest);
        add(features, "realizedVolatility20", realizedVolatility(candles, 20), latest);
        add(features, "downsideDeviation20", downsideDeviation(candles, 20), latest);
        add(features, "returnSkewness20", skewness(candles, 20), latest);
        add(features, "returnExcessKurtosis20", excessKurtosis(candles, 20), latest);
        add(features, "returnAutocorrelation1_20", autocorrelation(candles, 20), latest);
        add(features, "parkinsonVolatility20", parkinsonVolatility(candles, 20), latest);
        add(features, "garmanKlassVolatility20", garmanKlassVolatility(candles, 20), latest);
        add(features, "trendTStatistic20", trendTStatistic(candles, 20), latest);
        add(features, "zscore20", zScore(candles, 20), latest);
        add(features, "rvol20", relativeVolume(candles, 20), latest);
        add(features, "volumeZscore20", volumeZScore(candles, 20), latest);
        add(features, "amihudIlliquidity20", amihudIlliquidity(candles, 20), latest);
        add(features, "bodyRatio", ratio(latest.close().subtract(latest.open()).abs(), latest.high().subtract(latest.low())), latest);
        add(features, "upperWickRatio", ratio(latest.high().subtract(latest.open().max(latest.close())), latest.high().subtract(latest.low())), latest);
        add(features, "lowerWickRatio", ratio(latest.open().min(latest.close()).subtract(latest.low()), latest.high().subtract(latest.low())), latest);
        add(features, "emaSlope20", emaSlope(candles, 20), latest);
        add(features, "atrNormalized14", normalizedAtr(candles, 14), latest);
        add(features, "closeLocation", ratio(latest.close().subtract(latest.low()), latest.high().subtract(latest.low())), latest);
        return new FeatureSnapshot(latest.symbol(), latest.interval(), Instant.now(), features);
    }

    private void add(Map<String, FeatureValue> map, String name, BigDecimal value, Candle candle) {
        FeatureQuality quality = value == null ? FeatureQuality.INVALID : FeatureQuality.GOOD;
        map.put(name, new FeatureValue(name, value, candle.closeTime(), quality, "candles"));
    }

    private BigDecimal pct(BigDecimal from, BigDecimal to) {
        return from.signum() == 0 ? null : to.subtract(from).divide(from, MC);
    }

    private BigDecimal ratio(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? BigDecimal.ZERO : a.divide(b, MC);
    }

    private BigDecimal volatility(List<Candle> candles, int n) {
        double[] r = returns(candles, n);
        double mean = java.util.Arrays.stream(r).average().orElse(0);
        double variance = java.util.Arrays.stream(r).map(x -> Math.pow(x - mean, 2)).sum() / Math.max(1, r.length - 1);
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    private BigDecimal realizedVolatility(List<Candle> candles, int n) {
        double sumSquares = java.util.Arrays.stream(returns(candles, n)).map(value -> value * value).sum();
        return finite(Math.sqrt(sumSquares));
    }

    private BigDecimal downsideDeviation(List<Candle> candles, int n) {
        double[] values = returns(candles, n);
        double downsideSquares = java.util.Arrays.stream(values)
                .map(value -> Math.min(value, 0.0)).map(value -> value * value).sum();
        return finite(Math.sqrt(downsideSquares / values.length));
    }

    private BigDecimal skewness(List<Candle> candles, int n) {
        double[] values = returns(candles, n);
        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        double m2 = java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 2)).sum() / values.length;
        if (m2 == 0.0 || values.length < 3) return BigDecimal.ZERO;
        double m3 = java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 3)).sum() / values.length;
        double correction = Math.sqrt(values.length * (values.length - 1.0)) / (values.length - 2.0);
        return finite(correction * m3 / Math.pow(m2, 1.5));
    }

    private BigDecimal excessKurtosis(List<Candle> candles, int n) {
        double[] values = returns(candles, n);
        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        double sum2 = java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 2)).sum();
        if (sum2 == 0.0 || values.length < 4) return BigDecimal.ZERO;
        double sum4 = java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 4)).sum();
        double sample = values.length;
        double adjusted = sample * (sample + 1.0) * sum4
                / ((sample - 1.0) * (sample - 2.0) * (sample - 3.0) * Math.pow(sum2 / (sample - 1.0), 2))
                - 3.0 * Math.pow(sample - 1.0, 2) / ((sample - 2.0) * (sample - 3.0));
        return finite(adjusted);
    }

    private BigDecimal autocorrelation(List<Candle> candles, int n) {
        double[] values = returns(candles, n);
        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        double denominator = java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 2)).sum();
        if (denominator == 0.0) return BigDecimal.ZERO;
        double numerator = 0.0;
        for (int index = 1; index < values.length; index++) {
            numerator += (values[index] - mean) * (values[index - 1] - mean);
        }
        return finite(numerator / denominator);
    }

    private BigDecimal parkinsonVolatility(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double sum = window.stream().mapToDouble(candle -> {
            double logRange = Math.log(candle.high().doubleValue() / candle.low().doubleValue());
            return logRange * logRange;
        }).sum();
        return finite(Math.sqrt(sum / (4.0 * n * Math.log(2.0))));
    }

    private BigDecimal garmanKlassVolatility(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double variance = window.stream().mapToDouble(candle -> {
            double logRange = Math.log(candle.high().doubleValue() / candle.low().doubleValue());
            double logCloseOpen = Math.log(candle.close().doubleValue() / candle.open().doubleValue());
            return 0.5 * logRange * logRange - (2.0 * Math.log(2.0) - 1.0) * logCloseOpen * logCloseOpen;
        }).average().orElse(0.0);
        return finite(Math.sqrt(Math.max(variance, 0.0)));
    }

    private BigDecimal trendTStatistic(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double xMean = (n - 1.0) / 2.0;
        double yMean = window.stream().mapToDouble(candle -> Math.log(candle.close().doubleValue())).average().orElse(0.0);
        double xx = 0.0;
        double xy = 0.0;
        for (int index = 0; index < n; index++) {
            double centeredX = index - xMean;
            xx += centeredX * centeredX;
            xy += centeredX * (Math.log(window.get(index).close().doubleValue()) - yMean);
        }
        double slope = xy / xx;
        double residualSquares = 0.0;
        for (int index = 0; index < n; index++) {
            double fitted = yMean + slope * (index - xMean);
            residualSquares += Math.pow(Math.log(window.get(index).close().doubleValue()) - fitted, 2);
        }
        if (residualSquares == 0.0) return BigDecimal.ZERO;
        double standardError = Math.sqrt((residualSquares / (n - 2.0)) / xx);
        return finite(standardError == 0.0 ? 0.0 : slope / standardError);
    }

    private BigDecimal volumeZScore(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double mean = window.stream().mapToDouble(candle -> candle.volume().doubleValue()).average().orElse(0.0);
        double variance = window.stream().mapToDouble(candle -> Math.pow(candle.volume().doubleValue() - mean, 2)).sum() / n;
        double standardDeviation = Math.sqrt(variance);
        return finite(standardDeviation == 0.0 ? 0.0
                : (window.getLast().volume().doubleValue() - mean) / standardDeviation);
    }

    private BigDecimal amihudIlliquidity(List<Candle> candles, int n) {
        double[] logReturns = returns(candles, n);
        int start = candles.size() - n;
        double mean = 0.0;
        for (int index = 1; index < n; index++) {
            double dollarVolume = candles.get(start + index).close().doubleValue()
                    * candles.get(start + index).volume().doubleValue();
            if (dollarVolume > 0.0) mean += Math.abs(logReturns[index - 1]) / dollarVolume;
        }
        return finite(mean / logReturns.length * 1_000_000.0);
    }

    private BigDecimal finite(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value) : null;
    }

    private BigDecimal zScore(List<Candle> candles, int n) {
        int start = candles.size() - n;
        double mean = candles.subList(start, candles.size()).stream().mapToDouble(c -> c.close().doubleValue()).average().orElse(0);
        double variance = candles.subList(start, candles.size()).stream().mapToDouble(c -> Math.pow(c.close().doubleValue() - mean, 2)).sum() / n;
        double sd = Math.sqrt(variance);
        return BigDecimal.valueOf(sd == 0 ? 0 : (candles.get(candles.size()-1).close().doubleValue() - mean) / sd);
    }

    private BigDecimal relativeVolume(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size()-n, candles.size());
        double avg = window.stream().limit(n-1).mapToDouble(c -> c.volume().doubleValue()).average().orElse(0);
        return BigDecimal.valueOf(avg == 0 ? 0 : window.get(n-1).volume().doubleValue() / avg);
    }

    private BigDecimal emaSlope(List<Candle> candles, int period) {
        double alpha = 2.0 / (period + 1);
        double previous = candles.get(candles.size()-period-1).close().doubleValue();
        double beforeLast = previous;
        for (int i = candles.size()-period; i < candles.size(); i++) {
            previous = alpha * candles.get(i).close().doubleValue() + (1-alpha) * previous;
            if (i == candles.size()-2) beforeLast = previous;
        }
        return BigDecimal.valueOf(beforeLast == 0 ? 0 : (previous-beforeLast)/beforeLast);
    }

    private BigDecimal normalizedAtr(List<Candle> candles, int period) {
        double sum = 0;
        for (int i = candles.size()-period; i < candles.size(); i++) {
            Candle c = candles.get(i); Candle p = candles.get(i-1);
            double tr = Math.max(c.high().doubleValue()-c.low().doubleValue(), Math.max(Math.abs(c.high().doubleValue()-p.close().doubleValue()), Math.abs(c.low().doubleValue()-p.close().doubleValue())));
            sum += tr;
        }
        double close = candles.get(candles.size()-1).close().doubleValue();
        return BigDecimal.valueOf(close == 0 ? 0 : (sum/period)/close);
    }

    private double[] returns(List<Candle> candles, int n) {
        double[] values = new double[n-1]; int start = candles.size()-n;
        for (int i=1;i<n;i++) values[i-1] = Math.log(candles.get(start+i).close().doubleValue()/candles.get(start+i-1).close().doubleValue());
        return values;
    }
}
