package ai.aegis.automation;

import ai.aegis.attribution.StrategyAttributionStore;
import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import ai.aegis.orchestration.DecisionCycleResult;
import ai.aegis.orchestration.DecisionCycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeAutomationService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeAutomationService.class);

    private final CandleStore candleStore;
    private final DecisionCycleService decisionCycleService;
    private final StrategyAttributionStore attributionStore;
    private final List<String> symbols;
    private final List<String> intervals;
    private final boolean enabled;
    private final Map<String, DecisionCycleResult> latest = new ConcurrentHashMap<>();

    public RealtimeAutomationService(CandleStore candleStore,
                                     DecisionCycleService decisionCycleService,
                                     StrategyAttributionStore attributionStore,
                                     @Value("${aegis.market.symbols:BTCUSDT}") String symbols,
                                     @Value("${aegis.market.intervals:1m}") String intervals,
                                     @Value("${aegis.automation.enabled:true}") boolean enabled) {
        this.candleStore = candleStore;
        this.decisionCycleService = decisionCycleService;
        this.attributionStore = attributionStore;
        this.symbols = Arrays.stream(symbols.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(String::toUpperCase).distinct().toList();
        this.intervals = Arrays.stream(intervals.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${aegis.automation.delay-ms:15000}")
    public void runCycle() {
        if (!enabled) return;
        for (String symbol : symbols) for (String interval : intervals) {
            try {
                List<Candle> candles = candleStore.latest(symbol, interval, 250).stream().filter(Candle::closed).toList();
                if (candles.size() < 30) continue;
                DecisionCycleResult result = decisionCycleService.run(candles);
                attributionStore.save(result);
                latest.put(key(symbol, interval), result);
            } catch (Exception exception) {
                log.warn("Automated decision cycle failed for {} {}", symbol, interval, exception);
            }
        }
    }

    public DecisionCycleResult latest(String symbol, String interval) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank()) return null;
        return latest.get(key(symbol, interval));
    }

    private static String key(String symbol, String interval) { return symbol.toUpperCase() + ":" + interval; }
}
