package ai.aegis.analysis;

import ai.aegis.indicator.AtrIndicator;
import ai.aegis.indicator.EmaIndicator;
import ai.aegis.indicator.MacdIndicator;
import ai.aegis.indicator.RsiIndicator;
import ai.aegis.indicator.VwapIndicator;
import ai.aegis.market.Candle;
import ai.aegis.risk.RiskEngine;
import ai.aegis.risk.RiskPlan;
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
    private final MacdIndicator macdIndicator;
    private final VwapIndicator vwapIndicator;
    private final RiskEngine riskEngine;

    public MarketAnalysisService(EmaIndicator emaIndicator, RsiIndicator rsiIndicator,
                                 AtrIndicator atrIndicator, MacdIndicator macdIndicator,
                                 VwapIndicator vwapIndicator, RiskEngine riskEngine) {
        this.emaIndicator = emaIndicator;
        this.rsiIndicator = rsiIndicator;
        this.atrIndicator = atrIndicator;
        this.macdIndicator = macdIndicator;
        this.vwapIndicator = vwapIndicator;
        this.riskEngine = riskEngine;
    }

    public MarketAnalysis analyze(List<Candle> candles) {
        if (candles == null || candles.size() < 35) {
            throw new IllegalArgumentException("At least 35 candles are required for analysis");
        }

        Candle latest = candles.getLast();
        BigDecimal ema = emaIndicator.calculate(candles).values().get("ema");
        BigDecimal rsi = rsiIndicator.calculate(candles).values().get("rsi");
        BigDecimal atr = atrIndicator.calculate(candles).values().get("atr");
        Map<String, BigDecimal> macdValues = macdIndicator.calculate(candles).values();
        BigDecimal macdHistogram = macdValues.get("histogram");
        BigDecimal vwap = vwapIndicator.calculate(candles).values().get("vwap");

        int score = 50;
        List<String> reasons = new ArrayList<>();

        if (latest.close().compareTo(ema) > 0) {
            score += 15;
            reasons.add("Price is above EMA 20, supporting bullish trend alignment.");
        } else {
            score -= 15;
            reasons.add("Price is below EMA 20, supporting bearish trend alignment.");
        }

        if (latest.close().compareTo(vwap) > 0) {
            score += 10;
            reasons.add("Price is above VWAP, showing positive intraday value acceptance.");
        } else {
            score -= 10;
            reasons.add("Price is below VWAP, showing negative intraday value acceptance.");
        }

        if (macdHistogram.signum() > 0) {
            score += 12;
            reasons.add("MACD histogram is positive, confirming bullish momentum expansion.");
        } else {
            score -= 12;
            reasons.add("MACD histogram is negative, confirming bearish momentum pressure.");
        }

        if (rsi.compareTo(BigDecimal.valueOf(55)) >= 0 && rsi.compareTo(BigDecimal.valueOf(70)) < 0) {
            score += 13;
            reasons.add("RSI shows healthy bullish momentum without being overbought.");
        } else if (rsi.compareTo(BigDecimal.valueOf(45)) <= 0 && rsi.compareTo(BigDecimal.valueOf(30)) > 0) {
            score -= 13;
            reasons.add("RSI shows bearish momentum without being deeply oversold.");
        } else if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
            score -= 6;
            reasons.add("RSI is overbought, increasing pullback risk.");
        } else if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0) {
            score += 6;
            reasons.add("RSI is oversold, increasing rebound potential.");
        } else {
            reasons.add("RSI is neutral and contributes no directional edge.");
        }

        BigDecimal atrPercent = atr.multiply(BigDecimal.valueOf(100))
                .divide(latest.close(), 4, RoundingMode.HALF_UP);
        if (atrPercent.compareTo(BigDecimal.valueOf(3)) > 0) {
            score -= 12;
            reasons.add("ATR indicates elevated volatility, reducing trade quality.");
        } else if (atrPercent.compareTo(BigDecimal.valueOf(0.15)) < 0) {
            score -= 5;
            reasons.add("ATR is compressed, so breakout confirmation is still weak.");
        } else {
            score += 5;
            reasons.add("ATR indicates controlled volatility suitable for structured risk.");
        }

        score = Math.max(0, Math.min(100, score));
        String decision = score >= 68 ? "LONG" : score <= 32 ? "SHORT" : "WAIT";
        String grade = score >= 88 ? "A+" : score >= 78 ? "A" : score >= 68 ? "B" : score >= 42 ? "C" : "D";
        BigDecimal confidence = BigDecimal.valueOf(Math.abs(score - 50) * 2L).min(BigDecimal.valueOf(95));
        RiskPlan risk = riskEngine.build(decision, latest.close(), atr);

        if ("HIGH_RISK".equals(risk.status())) {
            decision = "WAIT";
            reasons.add("Risk engine rejected the setup because the ATR-based stop is too wide.");
            risk = riskEngine.build("WAIT", latest.close(), atr);
        }

        Map<String, BigDecimal> indicators = new LinkedHashMap<>();
        indicators.put("price", latest.close());
        indicators.put("ema20", ema.setScale(4, RoundingMode.HALF_UP));
        indicators.put("vwap", vwap.setScale(4, RoundingMode.HALF_UP));
        indicators.put("rsi14", rsi.setScale(2, RoundingMode.HALF_UP));
        indicators.put("atr14", atr.setScale(4, RoundingMode.HALF_UP));
        indicators.put("atrPercent", atrPercent);
        indicators.put("macd", macdValues.get("macd").setScale(4, RoundingMode.HALF_UP));
        indicators.put("macdSignal", macdValues.get("signal").setScale(4, RoundingMode.HALF_UP));
        indicators.put("macdHistogram", macdHistogram.setScale(4, RoundingMode.HALF_UP));

        return new MarketAnalysis(latest.symbol(), latest.interval(), decision, score, grade,
                confidence, Map.copyOf(indicators), risk, List.copyOf(reasons), Instant.now());
    }
}
