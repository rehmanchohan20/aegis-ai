package ai.aegis.api;

import ai.aegis.market.LiveMarketStreamService;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import ai.aegis.market.MarketSnapshotStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market-data")
public class RealtimeMarketController {
    private final MarketDataStateService state;
    private final MarketSnapshotStore snapshots;
    private final LiveMarketStreamService liveStream;

    public RealtimeMarketController(MarketDataStateService state,
                                    MarketSnapshotStore snapshots,
                                    LiveMarketStreamService liveStream) {
        this.state = state;
        this.snapshots = snapshots;
        this.liveStream = liveStream;
    }

    @GetMapping("/latest")
    public ResponseEntity<MarketSnapshot> latest(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        MarketSnapshot snapshot = state.latest(symbol);
        return snapshot == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(snapshot);
    }

    @GetMapping("/history")
    public List<MarketSnapshot> history(@RequestParam(defaultValue = "BTCUSDT") String symbol,
                                        @RequestParam(defaultValue = "300") int limit) {
        return snapshots.latest(symbol, limit);
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return state.status(); }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return liveStream.subscribe(symbol);
    }
}
