package ai.aegis.indicator;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public final class RsiIndicator implements Indicator {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final int period;

    public RsiIndicator() {
        this(14);
    }

    public RsiIndicator(int period) {
        if (period < 2) throw new IllegalArgumentException("period must be >= 2");
        this.period = period;
    }

    @Override
    public String name() {
        return "RSI_" + period;
    }

    @Override
    public IndicatorResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() <= period) {
            throw new IllegalArgumentException("RSI requires at least " + (period + 1) + " candles");
        }

        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        int start = candles.size() - period;

        for (int i = start; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close(), MC);
            if (change.signum() >= 0) gains = gains.add(change, MC);
            else losses = losses.add(change.abs(), MC);
        }

        BigDecimal averageGain = gains.divide(BigDecimal.valueOf(period), MC);
        BigDecimal averageLoss = losses.divide(BigDecimal.valueOf(period), MC);
        BigDecimal rsi;
        if (averageLoss.signum() == 0) {
            rsi = BigDecimal.valueOf(100);
        } else {
            BigDecimal rs = averageGain.divide(averageLoss, MC);
            rsi = BigDecimal.valueOf(100).subtract(
                    BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs, MC), MC), MC);
        }

        return new IndicatorResult(name(), Instant.now(), Map.of("rsi", rsi));
    }
}
