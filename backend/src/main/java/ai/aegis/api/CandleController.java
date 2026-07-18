package ai.aegis.api;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/candles")
public class CandleController {
    private final CandleStore candleStore;

    public CandleController(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    @GetMapping
    public List<Candle> latest(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit) {
        return candleStore.latest(symbol, interval, limit);
    }
}