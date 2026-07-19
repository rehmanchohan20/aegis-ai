package ai.aegis.setup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DirectionalBias(String symbol, String direction, BigDecimal confidence,
                              Map<String, TimeframeBias> timeframes, boolean higherTimeframesAligned,
                              List<String> reasons, Instant calculatedAt) {
    public DirectionalBias {
        timeframes = Map.copyOf(timeframes == null ? Map.of() : timeframes);
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
    public record TimeframeBias(String timeframe, String direction, BigDecimal score,
                                BigDecimal emaDistance, BigDecimal emaSlope, String structureState) { }
}
