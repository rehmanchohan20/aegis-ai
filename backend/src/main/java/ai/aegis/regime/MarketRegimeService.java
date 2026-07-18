package ai.aegis.regime;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketRegimeService {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public MarketRegime detect(List<Candle> candles) {
        if (candles == null || candles.size() < 50) {
            throw new IllegalArgumentException("At least 50 candles are required for regime detection");
        }

        List<Candle> window = candles.subList(candles.size() - 50, candles.size());
        BigDecimal first = window.get(0).close();
        BigDecimal last = window.get(window.size() - 1).close();
        BigDecimal high = window.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(last);
        BigDecimal low = window.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(last);

        BigDecimal netMovePercent = last.subtract(first, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .divide(first, MC);
        BigDecimal rangePercent = high.subtract(low, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .divide(last, MC);

        int higherHighs = 0;
        int higherLows = 0;
        int lowerHighs = 0;
        int lowerLows = 0;
        BigDecimal trueRangeSum = BigDecimal.ZERO;

        for (int i = 1; i < window.size(); i++) {
            Candle previous = window.get(i - 1);
            Candle current = window.get(i);
            if (current.high().compareTo(previous.high()) > 0) higherHighs++;
            if (current.low().compareTo(previous.low()) > 0) higherLows++;
            if (current.high().compareTo(previous.high()) < 0) lowerHighs++;
            if (current.low().compareTo(previous.low()) < 0) lowerLows++;

            BigDecimal highLow = current.high().subtract(current.low()).abs();
            BigDecimal highClose = current.high().subtract(previous.close()).abs();
            BigDecimal lowClose = current.low().subtract(previous.close()).abs();
            trueRangeSum = trueRangeSum.add(highLow.max(highClose).max(lowClose), MC);
        }

        BigDecimal averageTrueRange = trueRangeSum.divide(BigDecimal.valueOf(window.size() - 1L), MC);
        BigDecimal atrPercent = averageTrueRange.multiply(BigDecimal.valueOf(100), MC).divide(last, MC);
        BigDecimal trendStrength = netMovePercent.abs().divide(rangePercent.max(BigDecimal.valueOf(0.0001)), MC)
                .multiply(BigDecimal.valueOf(100), MC).min(BigDecimal.valueOf(100));

        String trend;
        if (netMovePercent.compareTo(BigDecimal.valueOf(1.25)) > 0 && higherHighs + higherLows > lowerHighs + lowerLows) {
            trend = "BULL_TREND";
        } else if (netMovePercent.compareTo(BigDecimal.valueOf(-1.25)) < 0 && lowerHighs + lowerLows > higherHighs + higherLows) {
            trend = "BEAR_TREND";
        } else {
            trend = "RANGE";
        }

        String volatility = atrPercent.compareTo(BigDecimal.valueOf(1.5)) >= 0 ? "HIGH"
                : atrPercent.compareTo(BigDecimal.valueOf(0.45)) <= 0 ? "LOW" : "NORMAL";

        String structure = switch (trend) {
            case "BULL_TREND" -> "HIGHER_HIGHS_HIGHER_LOWS";
            case "BEAR_TREND" -> "LOWER_HIGHS_LOWER_LOWS";
            default -> "MEAN_REVERTING";
        };

        List<String> reasons = new ArrayList<>();
        reasons.add("50-candle net move is " + netMovePercent.setScale(2, RoundingMode.HALF_UP) + "%.");
        reasons.add("Observed range is " + rangePercent.setScale(2, RoundingMode.HALF_UP) + "%.");
        reasons.add("ATR-based volatility is " + atrPercent.setScale(2, RoundingMode.HALF_UP) + "% of price.");
        reasons.add("Structure count: HH=" + higherHighs + ", HL=" + higherLows + ", LH=" + lowerHighs + ", LL=" + lowerLows + ".");

        return new MarketRegime(trend, volatility, structure,
                trendStrength.setScale(2, RoundingMode.HALF_UP),
                rangePercent.setScale(2, RoundingMode.HALF_UP),
                low, high, List.copyOf(reasons));
    }
}
