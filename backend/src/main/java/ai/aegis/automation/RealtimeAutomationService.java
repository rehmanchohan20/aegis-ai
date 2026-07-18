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
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RealtimeAutomationService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeAutomationService.class);

    private final CandleStore candleStore;
    private final DecisionCycleService decisionCycleService;
    private final StrategyAttributionStore attributionStore;
    private final String symbol;
    private final String interval;
    private final boolean enabled;
    private final AtomicReference<DecisionCycleResult> latest = new AtomicReference<>();

    public RealtimeAutomationService(CandleStore candleStore,
                                     DecisionCycleService decisionCycleService,
                                     StrategyAttributionStore attributionStore,
                                     @Value("${aegis.automation.symbol:BTCUSDT}") String symbol,
                                     @Value("${aegis.automation.interval:1m}") String interval,
                                     @Value("${aegis.automation.enabled:true}") boolean enabled) {
        this.candleStore = candleStore;
        this.decisionCycleService = decisionCycleService;
        this.attributionStore = attributionStore;
        this.symbol = symbol.toUpperCase();
        this.interval = interval;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${aegis.automation.delay-ms:15000}")
    public void runCycle() {
        if (!enabled) return;
        try {
            List<Candle> candles = candleStore.latest(symbol, interval, 250).stream().filter(Candle::closed).toList();
            if (candles.size() < 30) return;
            DecisionCycleResult result = decisionCycleService.run(candles);
            attributionStore.save(result);
            latest.set(result);
        } catch (Exception exception) {
            log.warn("Automated decision cycle failed for {} {}", symbol, interval, exception);
        }
    }

    public DecisionCycleResult latest() {
        return latest.get();
    }
}
