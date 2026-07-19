package ai.aegis.ranking;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class PairRankingService {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private final CandleStore candles;
    private final MarketDataStateService marketData;
    private final PaperTradingService paperTrading;
    private final List<String> symbols;

    public PairRankingService(CandleStore candles, MarketDataStateService marketData,
                              PaperTradingService paperTrading,
                              @Value("${aegis.market.symbols:BTCUSDT}") String symbols) {
        this.candles = candles;
        this.marketData = marketData;
        this.paperTrading = paperTrading;
        this.symbols = Arrays.stream(symbols.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(String::toUpperCase).distinct().toList();
    }

    public RankingSnapshot rank() {
        List<Candle> bitcoin = closed("BTCUSDT", 61);
        BigDecimal btcReturn = returnOver(bitcoin);
        List<PairScore> scores = symbols.stream().map(symbol -> score(symbol, bitcoin, btcReturn))
                .sorted(Comparator.comparing(PairScore::tradeQualityScore).reversed()).toList();
        long advancing = scores.stream().filter(score -> score.shortTermReturn().signum() > 0).count();
        String broadRegime = btcReturn.compareTo(BigDecimal.valueOf(.002)) > 0 && advancing * 2 >= scores.size()
                ? "RISK_ON" : btcReturn.compareTo(BigDecimal.valueOf(-.002)) < 0 && advancing * 2 < scores.size()
                ? "RISK_OFF" : "MIXED";
        return new RankingSnapshot(scores, broadRegime, btcReturn, Instant.now());
    }

    public CorrelationContext correlationContext(String symbol) {
        List<String> openSymbols = paperTrading.list().stream().filter(trade -> "OPEN".equals(trade.status()))
                .map(PaperTrade::symbol).distinct().toList();
        List<CorrelationExposure> exposures = openSymbols.stream().filter(open -> !open.equalsIgnoreCase(symbol))
                .map(open -> new CorrelationExposure(open, correlation(closed(symbol, 61), closed(open, 61))))
                .filter(exposure -> exposure.correlation().abs().compareTo(BigDecimal.valueOf(.75)) >= 0).toList();
        return new CorrelationContext(exposures.isEmpty(), exposures,
                exposures.isEmpty() ? "No highly correlated open paper exposure"
                        : "Highly correlated exposure exceeds configured threshold");
    }

    private PairScore score(String symbol, List<Candle> bitcoin, BigDecimal btcReturn) {
        MarketSnapshot live = marketData.latest(symbol);
        List<Candle> history = closed(symbol, 61);
        BigDecimal ownReturn = returnOver(history);
        BigDecimal btcRelative = ownReturn.subtract(btcReturn, MC);
        BigDecimal correlation = correlation(history, bitcoin);
        if (live == null) return new PairScore(symbol, BigDecimal.ZERO, ownReturn, btcRelative, correlation,
                null, null, "MISSING", List.of("No real-time market snapshot"));
        double score = "GOOD".equals(live.dataQuality()) ? 35 : 0;
        double spread = live.spreadBps() == null ? 100 : live.spreadBps().doubleValue();
        score += Math.max(0, 20 - spread);
        score += Math.min(15, Math.abs(live.orderBookImbalance() == null ? 0 : live.orderBookImbalance().doubleValue()) * 25);
        score += Math.min(15, Math.abs(ownReturn.doubleValue()) * 1200);
        score += Math.min(15, Math.log1p(Math.max(0, live.tradeIntensity().doubleValue())) * 3);
        List<String> warnings = new java.util.ArrayList<>();
        if (!"GOOD".equals(live.dataQuality())) warnings.add("Market data is " + live.dataQuality());
        if (spread > 20) warnings.add("Spread is excessive");
        return new PairScore(symbol, BigDecimal.valueOf(Math.min(100, score)), ownReturn, btcRelative,
                correlation, live.spreadBps(), live.orderBookImbalance(), live.dataQuality(), List.copyOf(warnings));
    }

    private List<Candle> closed(String symbol, int limit) {
        return candles.latest(symbol, "1m", limit).stream().filter(Candle::closed).toList();
    }

    private static BigDecimal returnOver(List<Candle> history) {
        if (history.size() < 2 || history.getFirst().close().signum() == 0) return BigDecimal.ZERO;
        return history.getLast().close().subtract(history.getFirst().close(), MC).divide(history.getFirst().close(), MC);
    }

    static BigDecimal correlation(List<Candle> left, List<Candle> right) {
        int count = Math.min(left.size(), right.size()) - 1;
        if (count < 10) return BigDecimal.ZERO;
        double[] x = new double[count];
        double[] y = new double[count];
        for (int index = 0; index < count; index++) {
            x[index] = Math.log(left.get(left.size() - count + index).close().doubleValue()
                    / left.get(left.size() - count + index - 1).close().doubleValue());
            y[index] = Math.log(right.get(right.size() - count + index).close().doubleValue()
                    / right.get(right.size() - count + index - 1).close().doubleValue());
        }
        double meanX = Arrays.stream(x).average().orElse(0);
        double meanY = Arrays.stream(y).average().orElse(0);
        double covariance = 0, varianceX = 0, varianceY = 0;
        for (int index = 0; index < count; index++) {
            covariance += (x[index] - meanX) * (y[index] - meanY);
            varianceX += Math.pow(x[index] - meanX, 2);
            varianceY += Math.pow(y[index] - meanY, 2);
        }
        double denominator = Math.sqrt(varianceX * varianceY);
        return BigDecimal.valueOf(denominator == 0 ? 0 : covariance / denominator);
    }

    public record PairScore(String symbol, BigDecimal tradeQualityScore, BigDecimal shortTermReturn,
                            BigDecimal btcRelativeReturn, BigDecimal btcCorrelation, BigDecimal spreadBps,
                            BigDecimal orderBookImbalance, String dataQuality, List<String> warnings) { }
    public record RankingSnapshot(List<PairScore> pairs, String broadMarketRegime,
                                  BigDecimal btcReturn, Instant calculatedAt) {
        public RankingSnapshot { pairs = List.copyOf(pairs); }
    }
    public record CorrelationExposure(String symbol, BigDecimal correlation) { }
    public record CorrelationContext(boolean approved, List<CorrelationExposure> exposures, String reason) {
        public CorrelationContext { exposures = List.copyOf(exposures); }
    }
}
