package ai.aegis.analysis;

import ai.aegis.indicator.AtrIndicator;
import ai.aegis.indicator.EmaIndicator;
import ai.aegis.indicator.RsiIndicator;
import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketAnalysisService {
    private final EmaIndicator emaIndicator;
    private final RsiIndicator rsiIndicator;
    private final AtrIndicator atrIndicator;

    public MarketAnalysisService(EmaIndicator emaIndicator, RsiIndicator rsiIndicator, AtrIndicator atrIndicator) {
        this.emaIndicator = emaIndicator;
        this.rsiIndicator = rsiIndicator;
        this.atrIndicator = atrIndicator;
    }

    public MarketAnalysis analyze(List<Candle> candles) {
        if (candles == null || candles.size() < 21) {
            throw new IllegalArgumentException("At least 21 candles are required for analysis");
        }

        Candle latest = candles.get(candles.size() - 1);
        BigDecimal ema = emaIndicator.calculate(candles).values().get("ema");
        BigDecimal rsi = rsiIndicator.calculate(candles).values().get("rsi");
        BigDecimal atr = atrIndicator.calculate(candles).values().get("atr");

        int score = 50;
        List<String> reasons = new ArrayList<>();

        if (latest.close().compareTo(ema) > 0) {
            score += 20;
            reasons.add("Price is trading above EMA 20, confirming bullish trend alignment.");
        } else {
            score -= 20;
            reasons.add("Price is trading below EMA 20, confirming bearish trend pressure.");
        }

        if (rsi.compareTo(BigDecimal.valueOf(55)) >= 0 && rsi.compareTo(BigDecimal.valueOf(70)) < 0) {
            score += 15;
            reasons.add("RSI shows healthy bullish momentum without being overbought.");
        } else if (rsi.compareTo(BigDecimal.valueOf(45)) <= 0 && rsi.compareTo(BigDecimal.valueOf(30)) > 0) {
            score -= 15;
            reasons.add("RSI shows bearish momentum without being deeply oversold.");
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
            score -= 5;
            reasons.add("RSI is overbought, increasing pullback risk.");
        } else if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0) {
            score += 5;
            reasons.add("RSI is oversold, increasing rebound potential.");
        } else {
            reasons.add("RSI is neutral and contributes no directional edge.");
        }

        BigDecimal atrPercent = atr.multiply(BigDecimal.valueOf(100))
                .divide(latest.close(), 4, RoundingMode.HALF_UP);
        if (atrPercent.compareTo(BigDecimal.valueOf(2.5)) > 0) {
            score -= 10;
            reasons.add("ATR indicates elevated volatility, so confidence is reduced.");
        } else {
            score += 5;
            reasons.add("ATR indicates controlled volatility suitable for structured risk.");
        }

        score = Math.max(0, Math.min(100, score));
        String decision = score >= 65 ? "LONG" : score <= 35 ? "SHORT" : "WAIT";
        String grade = score >= 85 ? "A+" : score >= 75 ? "A" : score >= 65 ? "B" : score >= 45 ? "C" : "D";
        BigDecimal confidence = BigDecimal.valueOf(Math.abs(score - 50) * 2L)
                .min(BigDecimal.valueOf(95));

        Map<String, BigDecimal> indicators = new LinkedHashMap<>();
        indicators.put("price", latest.close());
        indicators.put("ema20", ema.setScale(4, RoundingMode.HALF_UP));
        indicators.put("rsi14", rsi.setScale(2, RoundingMode.HALF_UP));
        indicators.put("atr14", atr.setScale(4, RoundingMode.HALF_UP));
        indicators.put("atrPercent", atrPercent);

        return new MarketAnalysis(latest.symbol(), latest.interval(), decision, score, grade,
                confidence, indicators, List.copyOf(reasons), Instant.now());
    }
}
