package ai.aegis.setup;

import ai.aegis.feature.CandleFeatureService;
import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import ai.aegis.ml.MlPrediction;
import ai.aegis.ml.MlPredictionClient;
import ai.aegis.structure.MarketStructureService;
import ai.aegis.structure.MarketStructureSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DirectionalSetupService {
    private static final Logger log = LoggerFactory.getLogger(DirectionalSetupService.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private final CandleStore candles; private final VolumeProfileService profiles;
    private final DirectionalBiasService biases; private final MarketStructureService structures;
    private final MarketDataStateService marketData; private final CandleFeatureService features;
    private final MlPredictionClient predictions; private final DirectionalSetupStore store;
    private final List<String> symbols; private final List<String> timeframes;
    private final Map<String, DirectionalSetup> latest = new ConcurrentHashMap<>();

    public DirectionalSetupService(CandleStore candles, VolumeProfileService profiles,
                                   DirectionalBiasService biases, MarketStructureService structures,
                                   MarketDataStateService marketData, CandleFeatureService features,
                                   MlPredictionClient predictions, DirectionalSetupStore store,
                                   @Value("${aegis.market.symbols:BTCUSDT}") String symbols,
                                   @Value("${aegis.setup.timeframes:1m,3m,5m,15m}") String timeframes) {
        this.candles = candles; this.profiles = profiles; this.biases = biases; this.structures = structures;
        this.marketData = marketData; this.features = features; this.predictions = predictions; this.store = store;
        this.symbols = csv(symbols, true); this.timeframes = csv(timeframes, false);
    }

    @Scheduled(initialDelayString = "${aegis.setup.initial-delay-ms:30000}",
            fixedDelayString = "${aegis.setup.delay-ms:15000}")
    public void refresh() {
        for (String symbol : symbols) for (String timeframe : timeframes) {
            try { DirectionalSetup setup = analyze(symbol, timeframe); store.save(setup); }
            catch (RuntimeException error) { log.error("Directional setup evaluation failed for {} {}", symbol, timeframe, error); }
        }
    }

    public DirectionalSetup analyze(String symbol, String timeframe) {
        List<Candle> history = candles.latest(symbol, timeframe, 400).stream().filter(Candle::closed).toList();
        if (history.size() < 80) throw new IllegalArgumentException("directional setup requires at least 80 closed candles");
        DirectionalBias bias = biases.analyze(symbol);
        VolumeProfile fixed = profiles.fixedRange(history.subList(Math.max(0, history.size() - 180), history.size()), 48);
        VolumeProfile session = profiles.session(history, 36);
        MarketStructureSnapshot structure = structures.analyze(history);
        MarketSnapshot market = marketData.latest(symbol);
        DirectionalSetup preliminary = evaluate(history, bias, fixed, session, structure, market, null, true);
        MlPrediction prediction = null;
        if (!"WAIT".equals(bias.direction()) && preliminary.breakOfStructureConfirmed()
                && (preliminary.pullbackIntoValue() || preliminary.breakoutRetest() || preliminary.reclaimOrRejection())) {
            try { prediction = predictions.predict(features.build(history)); }
            catch (RuntimeException unavailable) {
                log.warn("Calibrated probability unavailable for {} {}; setup remains non-actionable: {}",
                        symbol, timeframe, unavailable.getMessage());
            }
        }
        DirectionalSetup result = evaluate(history, bias, fixed, session, structure, market, prediction, true);
        latest.put(key(symbol, timeframe), result);
        return result;
    }

    public DirectionalSetup latest(String symbol, String timeframe) {
        DirectionalSetup inMemory = latest.get(key(symbol, timeframe));
        if (inMemory != null) return inMemory;
        DirectionalSetup persisted = store.latest(symbol, timeframe);
        return persisted == null ? analyze(symbol, timeframe) : persisted;
    }

    DirectionalSetup evaluateHistorical(List<Candle> history, DirectionalBias bias) {
        List<Candle> closed = history.stream().filter(Candle::closed).toList();
        VolumeProfile fixed = profiles.fixedRange(closed.subList(Math.max(0, closed.size() - 180), closed.size()), 48);
        VolumeProfile session = profiles.session(closed, 36);
        MarketStructureSnapshot structure = structures.analyze(closed);
        return evaluate(closed, bias, fixed, session, structure, null, null, false);
    }

    private DirectionalSetup evaluate(List<Candle> history, DirectionalBias bias, VolumeProfile fixed,
                                      VolumeProfile session, MarketStructureSnapshot structure,
                                      MarketSnapshot market, MlPrediction prediction, boolean liveMode) {
        Candle current = history.getLast(); String direction = bias.direction(); boolean bullish = "LONG".equals(direction);
        BigDecimal atr = structure.atr(); BigDecimal vwap = vwap(history.subList(Math.max(0, history.size() - 80), history.size()));
        BreakoutContext breakout = breakout(history, atr, direction);
        boolean structureDirection = bullish ? structure.structureState().startsWith("BULL") : "SHORT".equals(direction) && structure.structureState().startsWith("BEAR");
        boolean bos = !"WAIT".equals(direction) && (breakout.confirmed() || structure.markers().stream()
                .anyMatch(marker -> "BREAK_OF_STRUCTURE".equals(marker.type()) && direction.equals(marker.direction())));
        boolean pullback = !"WAIT".equals(direction) && current.low().compareTo(fixed.valueAreaHigh()) <= 0
                && current.high().compareTo(fixed.valueAreaLow()) >= 0;
        boolean retest = breakout.retest();
        boolean reclaim = (bullish && ("REJECTION_BELOW_VALUE".equals(fixed.volumeState())
                || current.low().compareTo(vwap) < 0 && current.close().compareTo(vwap) > 0))
                || ("SHORT".equals(direction) && ("REJECTION_ABOVE_VALUE".equals(fixed.volumeState())
                || current.high().compareTo(vwap) > 0 && current.close().compareTo(vwap) < 0));
        BigDecimal anchor = nearestAnchor(current.close(), Arrays.asList(fixed.pointOfControl(), vwap,
                bullish ? structure.supportPrice() : structure.resistancePrice(), breakout.level()));
        DirectionalSetup.PriceZone entryZone = "WAIT".equals(direction) ? null : new DirectionalSetup.PriceZone(
                anchor.subtract(atr.multiply(BigDecimal.valueOf(.25), MC), MC),
                anchor.add(atr.multiply(BigDecimal.valueOf(.25), MC), MC),
                basis(anchor, fixed, vwap, structure, breakout));
        BigDecimal entry = entryZone == null ? current.close() : entryZone.lower().add(entryZone.upper()).divide(BigDecimal.TWO, MC);
        BigDecimal stop = entryZone == null ? null : bullish
                ? min(entryZone.lower(), structure.supportPrice(), fixed.valueAreaLow()).subtract(atr.multiply(BigDecimal.valueOf(.25), MC), MC)
                : max(entryZone.upper(), structure.resistancePrice(), fixed.valueAreaHigh()).add(atr.multiply(BigDecimal.valueOf(.25), MC), MC);
        BigDecimal risk = stop == null ? BigDecimal.ZERO : entry.subtract(stop).abs();
        List<BigDecimal> liquidity = liquidityTargets(bullish, entry, fixed, session, structure);
        List<BigDecimal> qualifiedTargets = risk.signum() == 0 ? List.of() : liquidity.stream()
                .filter(target -> target.subtract(entry).abs().divide(risk, MC).compareTo(BigDecimal.valueOf(2.5)) >= 0)
                .limit(3).toList();
        BigDecimal expectedR = qualifiedTargets.isEmpty() || risk.signum() == 0 ? BigDecimal.ZERO
                : qualifiedTargets.getFirst().subtract(entry).abs().divide(risk, MC);
        boolean flowConflict = liveMode && strongFlowConflict(direction, market);
        boolean executionValid = !liveMode || market != null && "GOOD".equals(market.dataQuality())
                && market.spreadBps() != null && market.spreadBps().compareTo(BigDecimal.valueOf(20)) <= 0
                && market.bidDepth() != null && market.askDepth() != null && market.bidDepth().signum() > 0 && market.askDepth().signum() > 0;
        BigDecimal targetProbability = prediction == null ? historicalProbability(history, direction, risk, qualifiedTargets, entry) : prediction.targetHitProbability();
        BigDecimal stopProbability = prediction == null ? targetProbability == null ? null : BigDecimal.ONE.subtract(targetProbability, MC) : prediction.stopHitProbability();
        boolean probabilityValid = !liveMode || prediction != null && targetProbability != null && stopProbability != null
                && targetProbability.compareTo(stopProbability) > 0 && targetProbability.compareTo(BigDecimal.valueOf(.5)) > 0
                && direction.equals(prediction.decision());
        List<String> confirmations = new ArrayList<>(), rejections = new ArrayList<>(), warnings = new ArrayList<>();
        require(bias.higherTimeframesAligned() && !"WAIT".equals(direction), "Higher-timeframe directional bias aligned", "Higher-timeframe bias is neutral or conflicting", confirmations, rejections);
        require(bos && structureDirection, "Confirmed " + direction + " break of structure", "Directional break of structure is not confirmed", confirmations, rejections);
        require(pullback || retest || reclaim, pullback ? "Price pulled back into value" : retest ? "Breakout retest held" : "Value/VWAP reclaim or rejection confirmed", "No qualified pullback, retest, reclaim, or rejection", confirmations, rejections);
        require(!flowConflict, "No strong opposing order-flow imbalance", "Strong opposing order-flow conflict", confirmations, rejections);
        require(executionValid, "Spread, freshness, and top-book liquidity are valid", "Spread, freshness, or liquidity validation failed", confirmations, rejections);
        require(expectedR.compareTo(BigDecimal.valueOf(2.5)) >= 0, "Liquidity target offers at least 2.5R", "No defensible liquidity target offers 2.5R", confirmations, rejections);
        if (liveMode) require(probabilityValid, "Calibrated target-first probability exceeds stop-first probability", "Approved calibrated target-first probability is unavailable or insufficient", confirmations, rejections);
        else warnings.add("Historical probability is a diffusion proxy; realized backtest outcomes are the validation evidence");
        if (market == null) warnings.add("Historical candles do not contain spread or order-book state");
        if (prediction == null && liveMode) warnings.add("ML was not invoked or no approved compatible artifact was available");
        double quality = 20 * bias.confidence().doubleValue() + (bos ? 20 : 0) + (pullback || retest || reclaim ? 15 : 0)
                + (!flowConflict ? 10 : 0) + (executionValid ? 10 : 0) + Math.min(15, expectedR.doubleValue() * 4)
                + (targetProbability == null ? 0 : targetProbability.doubleValue() * 10);
        String status = rejections.isEmpty() ? "APPROVED_SETUP" : "WAIT".equals(direction) ? "SEARCHING_BIAS"
                : !bos ? "WAITING_STRUCTURE" : "WATCHLIST";
        String setupType = retest ? "BREAKOUT_RETEST" : pullback ? "PULLBACK_INTO_VALUE" : reclaim ? "RECLAIM_REJECTION" : "NONE";
        List<DirectionalSetup.ChartMarker> markers = new ArrayList<>();
        if (breakout.time() != null) markers.add(new DirectionalSetup.ChartMarker(breakout.time(), breakout.level(), "BREAKOUT", direction, "BOS"));
        if (retest) markers.add(new DirectionalSetup.ChartMarker(current.openTime(), current.close(), "RETEST", direction, "Retest"));
        structure.markers().stream().filter(marker -> List.of("BREAK_OF_STRUCTURE", "CHANGE_OF_CHARACTER").contains(marker.type()))
                .forEach(marker -> markers.add(new DirectionalSetup.ChartMarker(marker.time(), marker.price(), marker.type(), marker.direction(), marker.type())));
        return new DirectionalSetup(UUID.randomUUID(), current.symbol(), current.interval(), Instant.now(), status,
                direction, bias, fixed, session, structure.structureState(), setupType, fixed.volumeState(), bos,
                breakout.confirmed(), pullback, retest, reclaim, entryZone, stop, qualifiedTargets, expectedR,
                stopProbability, targetProbability, BigDecimal.valueOf(Math.min(100, quality)),
                prediction == null ? liveMode ? "UNAVAILABLE" : "DIFFUSION_PROXY_UNCALIBRATED" : "CALIBRATED_MODEL:" + prediction.model(),
                List.copyOf(markers), confirmations, rejections, warnings);
    }

    private static BreakoutContext breakout(List<Candle> history, BigDecimal atr, String direction) {
        if ("WAIT".equals(direction) || history.size() < 35) return new BreakoutContext(false, false, null, null);
        for (int index = history.size() - 2; index >= Math.max(25, history.size() - 14); index--) {
            List<Candle> prior = history.subList(index - 20, index);
            BigDecimal level = "LONG".equals(direction) ? prior.stream().map(Candle::high).max(BigDecimal::compareTo).orElseThrow()
                    : prior.stream().map(Candle::low).min(BigDecimal::compareTo).orElseThrow();
            BigDecimal averageVolume = prior.stream().map(Candle::volume).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(prior.size()), MC);
            Candle breakCandle = history.get(index);
            boolean confirmed = "LONG".equals(direction) ? breakCandle.close().compareTo(level.add(atr.multiply(BigDecimal.valueOf(.1), MC))) > 0
                    : breakCandle.close().compareTo(level.subtract(atr.multiply(BigDecimal.valueOf(.1), MC))) < 0;
            confirmed &= breakCandle.volume().compareTo(averageVolume.multiply(BigDecimal.valueOf(1.15), MC)) > 0;
            if (!confirmed) continue;
            boolean retest = history.subList(index + 1, history.size()).stream().anyMatch(c -> "LONG".equals(direction)
                    ? c.low().compareTo(level.add(atr.multiply(BigDecimal.valueOf(.25), MC))) <= 0 && c.close().compareTo(level) >= 0
                    : c.high().compareTo(level.subtract(atr.multiply(BigDecimal.valueOf(.25), MC))) >= 0 && c.close().compareTo(level) <= 0);
            return new BreakoutContext(true, retest, level, breakCandle.closeTime());
        }
        return new BreakoutContext(false, false, history.getLast().close(), null);
    }

    private static boolean strongFlowConflict(String direction, MarketSnapshot market) {
        if (market == null || market.orderBookImbalance() == null) return false;
        return "LONG".equals(direction) && market.orderBookImbalance().compareTo(BigDecimal.valueOf(-.35)) < 0
                || "SHORT".equals(direction) && market.orderBookImbalance().compareTo(BigDecimal.valueOf(.35)) > 0;
    }
    private static List<BigDecimal> liquidityTargets(boolean bullish, BigDecimal entry, VolumeProfile fixed,
                                                      VolumeProfile session, MarketStructureSnapshot structure) {
        LinkedHashSet<BigDecimal> candidates = new LinkedHashSet<>();
        candidates.add(bullish ? fixed.valueAreaHigh() : fixed.valueAreaLow());
        candidates.add(bullish ? session.valueAreaHigh() : session.valueAreaLow());
        candidates.add(bullish ? fixed.rangeHigh() : fixed.rangeLow());
        candidates.add(bullish ? structure.resistancePrice() : structure.supportPrice());
        fixed.highVolumeNodes().forEach(node -> candidates.add(node.midpoint()));
        Comparator<BigDecimal> order = bullish ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return candidates.stream().filter(value -> value != null && (bullish ? value.compareTo(entry) > 0 : value.compareTo(entry) < 0)).sorted(order).toList();
    }
    private static BigDecimal historicalProbability(List<Candle> history, String direction, BigDecimal risk,
                                                    List<BigDecimal> targets, BigDecimal entry) {
        if ("WAIT".equals(direction) || risk.signum() <= 0 || targets.isEmpty()) return null;
        List<Double> returns = new ArrayList<>();
        for (int i=Math.max(1,history.size()-80);i<history.size();i++) returns.add(Math.log(history.get(i).close().doubleValue()/history.get(i-1).close().doubleValue()));
        double rawMean=returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mean="SHORT".equals(direction)?-rawMean:rawMean;
        double variance=returns.stream().mapToDouble(v->(v-rawMean)*(v-rawMean)).average().orElse(0);
        double a=risk.divide(entry,MC).doubleValue(), b=targets.getFirst().subtract(entry).abs().divide(entry,MC).doubleValue();
        if(variance<1e-12) return BigDecimal.valueOf(a/(a+b));
        double numerator=1-Math.exp(-2*mean*a/variance), denominator=1-Math.exp(-2*mean*(a+b)/variance);
        double probability=Math.abs(denominator)<1e-9?a/(a+b):numerator/denominator;
        return BigDecimal.valueOf(Math.max(.01,Math.min(.99,probability)));
    }
    static BigDecimal nearestAnchor(BigDecimal price, List<BigDecimal> values) { return values.stream().filter(v->v!=null).min(Comparator.comparing(v->v.subtract(price).abs())).orElse(price); }
    private static String basis(BigDecimal anchor, VolumeProfile profile, BigDecimal vwap, MarketStructureSnapshot structure, BreakoutContext breakout) {
        if(anchor.compareTo(profile.pointOfControl())==0)return "POINT_OF_CONTROL"; if(anchor.compareTo(vwap)==0)return "VWAP";
        if(breakout.level()!=null&&anchor.compareTo(breakout.level())==0)return "BREAKOUT_RETEST";
        boolean support = structure.supportPrice() != null && anchor.compareTo(structure.supportPrice()) == 0;
        boolean resistance = structure.resistancePrice() != null && anchor.compareTo(structure.resistancePrice()) == 0;
        return support || resistance ? "STRUCTURE" : "VALUE";
    }
    private static BigDecimal vwap(List<Candle> candles){BigDecimal pv=BigDecimal.ZERO,v=BigDecimal.ZERO;for(Candle c:candles){BigDecimal t=c.high().add(c.low()).add(c.close()).divide(BigDecimal.valueOf(3),MC);pv=pv.add(t.multiply(c.volume(),MC));v=v.add(c.volume());}return v.signum()==0?candles.getLast().close():pv.divide(v,MC);}
    private static BigDecimal min(BigDecimal... values){return Arrays.stream(values).filter(v->v!=null).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);}
    private static BigDecimal max(BigDecimal... values){return Arrays.stream(values).filter(v->v!=null).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);}
    private static void require(boolean condition,String yes,String no,List<String> confirmations,List<String> rejections){if(condition)confirmations.add(yes);else rejections.add(no);}
    private static List<String> csv(String value,boolean uppercase){return Arrays.stream(value.split(",")).map(String::trim).filter(v->!v.isBlank()).map(v->uppercase?v.toUpperCase():v).distinct().toList();}
    private static String key(String symbol,String timeframe){return symbol.toUpperCase()+":"+timeframe;}
    private record BreakoutContext(boolean confirmed,boolean retest,BigDecimal level,Instant time){}
}
