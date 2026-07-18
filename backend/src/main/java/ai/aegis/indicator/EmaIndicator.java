package ai.aegis.indicator;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public final class EmaIndicator implements Indicator {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final int period;

    public EmaIndicator() {
        this(20);
    }

    public EmaIndicator(int period) {
        if (period < 2) throw new IllegalArgumentException("period must be >= 2");
        this.period = period;
    }

    @Override
    public String name() {
        return "EMA_" + period;
    }

    @Override
    public IndicatorResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            throw new IllegalArgumentException("EMA requires at least " + period + " candles");
        }

        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1.0));
        BigDecimal ema = candles.subList(0, period).stream()
                .map(Candle::close)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), MC);

        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            ema = close.subtract(ema, MC).multiply(multiplier, MC).add(ema, MC);
        }

        Candle latest = candles.get(candles.size() - 1);
        return new IndicatorResult(name(), Instant.now(), Map.of("ema", ema, "price", latest.close()));
    }
}
