package ai.aegis.indicator;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MacdIndicator implements Indicator {
    private static final MathContext MC = MathContext.DECIMAL64;

    @Override
    public String name() {
        return "MACD_12_26_9";
    }

    @Override
    public IndicatorResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 35) {
            throw new IllegalArgumentException("MACD requires at least 35 candles");
        }

        BigDecimal ema12 = candles.getFirst().close();
        BigDecimal ema26 = candles.getFirst().close();
        BigDecimal signal = BigDecimal.ZERO;
        BigDecimal alpha12 = BigDecimal.valueOf(2.0 / 13.0);
        BigDecimal alpha26 = BigDecimal.valueOf(2.0 / 27.0);
        BigDecimal alpha9 = BigDecimal.valueOf(2.0 / 10.0);
        BigDecimal macd = BigDecimal.ZERO;

        for (int i = 1; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            ema12 = close.multiply(alpha12, MC).add(ema12.multiply(BigDecimal.ONE.subtract(alpha12), MC), MC);
            ema26 = close.multiply(alpha26, MC).add(ema26.multiply(BigDecimal.ONE.subtract(alpha26), MC), MC);
            macd = ema12.subtract(ema26, MC);
            signal = i == 1 ? macd : macd.multiply(alpha9, MC)
                    .add(signal.multiply(BigDecimal.ONE.subtract(alpha9), MC), MC);
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("macd", macd);
        values.put("signal", signal);
        values.put("histogram", macd.subtract(signal, MC));
        return new IndicatorResult(name(), Instant.now(), Map.copyOf(values));
    }
}
