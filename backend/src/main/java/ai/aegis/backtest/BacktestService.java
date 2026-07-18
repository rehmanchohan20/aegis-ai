package ai.aegis.backtest;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BacktestService {
    private static final int FAST = 20;
    private static final int SLOW = 50;

    public BacktestResult run(BacktestRequest request) {
        List<Candle> candles = request.candles();
        BigDecimal balance = request.initialBalance();
        BigDecimal peak = balance;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        List<BacktestTrade> trades = new ArrayList<>();

        for (int i = SLOW; i < candles.size() - 1; i++) {
            BigDecimal fastPrev = ema(candles, i - 1, FAST);
            BigDecimal slowPrev = ema(candles, i - 1, SLOW);
            BigDecimal fastNow = ema(candles, i, FAST);
            BigDecimal slowNow = ema(candles, i, SLOW);

            boolean longSignal = fastPrev.compareTo(slowPrev) <= 0 && fastNow.compareTo(slowNow) > 0;
            boolean shortSignal = fastPrev.compareTo(slowPrev) >= 0 && fastNow.compareTo(slowNow) < 0;
            if (!longSignal && !shortSignal) continue;

            Candle entryCandle = candles.get(i + 1);
            BigDecimal entry = entryCandle.open();
            BigDecimal atr = atr(candles, i, 14);
            if (atr.signum() <= 0) continue;

            String direction = longSignal ? "LONG" : "SHORT";
            BigDecimal stopDistance = atr.multiply(BigDecimal.valueOf(1.5));
            BigDecimal stop = longSignal ? entry.subtract(stopDistance) : entry.add(stopDistance);
            BigDecimal target = longSignal ? entry.add(stopDistance.multiply(BigDecimal.valueOf(2)))
                    : entry.subtract(stopDistance.multiply(BigDecimal.valueOf(2)));

            BigDecimal riskCash = balance.multiply(request.riskPercent())
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            BigDecimal quantity = riskCash.divide(stopDistance, 8, RoundingMode.HALF_UP);

            Candle exitCandle = entryCandle;
            BigDecimal exit = entry;
            String exitReason = "TIMEOUT";
            int maxBars = Math.min(candles.size(), i + 1 + 60);
            for (int j = i + 1; j < maxBars; j++) {
                Candle c = candles.get(j);
                exitCandle = c;
                if (longSignal && c.low().compareTo(stop) <= 0) { exit = stop; exitReason = "STOP"; break; }
                if (longSignal && c.high().compareTo(target) >= 0) { exit = target; exitReason = "TARGET"; break; }
                if (shortSignal && c.high().compareTo(stop) >= 0) { exit = stop; exitReason = "STOP"; break; }
                if (shortSignal && c.low().compareTo(target) <= 0) { exit = target; exitReason = "TARGET"; break; }
                exit = c.close();
            }

            BigDecimal priceMove = longSignal ? exit.subtract(entry) : entry.subtract(exit);
            BigDecimal pnl = priceMove.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
            BigDecimal before = balance;
            balance = balance.add(pnl);
            BigDecimal returnPercent = before.signum() == 0 ? BigDecimal.ZERO : pnl.multiply(BigDecimal.valueOf(100))
                    .divide(before, 4, RoundingMode.HALF_UP);

            if (pnl.signum() >= 0) grossProfit = grossProfit.add(pnl); else grossLoss = grossLoss.add(pnl.abs());
            peak = peak.max(balance);
            if (peak.signum() > 0) {
                BigDecimal dd = peak.subtract(balance).multiply(BigDecimal.valueOf(100))
                        .divide(peak, 4, RoundingMode.HALF_UP);
                maxDrawdown = maxDrawdown.max(dd);
            }

            trades.add(new BacktestTrade(direction, entryCandle.openTime(), exitCandle.closeTime(), entry, exit,
                    stop, target, pnl, returnPercent, exitReason));
        }

        int wins = (int) trades.stream().filter(t -> t.pnl().signum() > 0).count();
        int losses = trades.size() - wins;
        BigDecimal winRate = trades.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins * 100.0 / trades.size())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = balance.subtract(request.initialBalance());
        BigDecimal totalReturn = net.multiply(BigDecimal.valueOf(100))
                .divide(request.initialBalance(), 4, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? grossProfit : grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);

        List<String> warnings = new ArrayList<>();
        if (trades.size() < 20) warnings.add("Sample size is too small for reliable conclusions.");
        if (maxDrawdown.compareTo(BigDecimal.valueOf(20)) > 0) warnings.add("Maximum drawdown exceeds the 20% safety threshold.");
        if (profitFactor.compareTo(BigDecimal.valueOf(1.2)) < 0) warnings.add("Profit factor is below the minimum 1.20 acceptance threshold.");
        boolean accepted = trades.size() >= 20 && maxDrawdown.compareTo(BigDecimal.valueOf(20)) <= 0
                && profitFactor.compareTo(BigDecimal.valueOf(1.2)) >= 0 && net.signum() > 0;

        return new BacktestResult(trades.size(), wins, losses, winRate, net, totalReturn, maxDrawdown,
                profitFactor, balance, accepted, List.copyOf(warnings), List.copyOf(trades));
    }

    private BigDecimal ema(List<Candle> candles, int end, int period) {
        int start = Math.max(0, end - period * 3);
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal value = candles.get(start).close();
        for (int i = start + 1; i <= end; i++) {
            value = candles.get(i).close().subtract(value).multiply(multiplier).add(value);
        }
        return value;
    }

    private BigDecimal atr(List<Candle> candles, int end, int period) {
        int start = Math.max(1, end - period + 1);
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i <= end; i++) {
            Candle c = candles.get(i);
            BigDecimal previousClose = candles.get(i - 1).close();
            BigDecimal tr = c.high().subtract(c.low()).max(c.high().subtract(previousClose).abs())
                    .max(c.low().subtract(previousClose).abs());
            total = total.add(tr);
            count++;
        }
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }
}
