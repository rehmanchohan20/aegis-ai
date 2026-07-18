package ai.aegis.indicator;

import ai.aegis.market.Candle;
import java.util.List;

public interface Indicator {
    String name();
    IndicatorResult calculate(List<Candle> candles);
}
