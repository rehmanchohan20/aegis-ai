package ai.aegis.indicator;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class VwapIndicator implements Indicator {
    private static final MathContext MC = MathContext.DECIMAL64;

    @Override
    public String name() {
        return "VWAP";
    }

    @Override
    public IndicatorResult calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("VWAP requires candles");
        }

        BigDecimal priceVolume = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (Candle candle : candles) {
            BigDecimal typical = candle.high().add(candle.low(), MC).add(candle.close(), MC)
                    .divide(BigDecimal.valueOf(3), MC);
            priceVolume = priceVolume.add(typical.multiply(candle.volume(), MC), MC);
            totalVolume = totalVolume.add(candle.volume(), MC);
        }
        if (totalVolume.signum() == 0) {
            throw new IllegalArgumentException("VWAP cannot be calculated with zero volume");
        }
        return new IndicatorResult(name(), Instant.now(), Map.of("vwap", priceVolume.divide(totalVolume, MC)));
    }
}
