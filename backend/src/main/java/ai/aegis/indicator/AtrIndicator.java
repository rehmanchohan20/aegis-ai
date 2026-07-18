package ai.aegis.indicator;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public final class AtrIndicator implements Indicator {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final int period;

    public AtrIndicator() {
        this(14);
    }

    public AtrIndicator(int period) {
        if (period < 2) throw new IllegalArgumentException("period must be >= 2");
        this.period = period;
    }

    @Override
    public String name() {
        return "ATR_" + period;
    }

    @Override
    public IndicatorResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() <= period) {
            throw new IllegalArgumentException("ATR requires at least " + (period + 1) + " candles");
        }

        BigDecimal total = BigDecimal.ZERO;
        int start = candles.size() - period;
        for (int i = start; i < candles.size(); i++) {
            Candle current = candles.get(i);
            BigDecimal previousClose = candles.get(i - 1).close();
            BigDecimal highLow = current.high().subtract(current.low(), MC).abs();
            BigDecimal highClose = current.high().subtract(previousClose, MC).abs();
            BigDecimal lowClose = current.low().subtract(previousClose, MC).abs();
            total = total.add(highLow.max(highClose).max(lowClose), MC);
        }

        BigDecimal atr = total.divide(BigDecimal.valueOf(period), MC);
        return new IndicatorResult(name(), Instant.now(), Map.of("atr", atr));
    }
}
