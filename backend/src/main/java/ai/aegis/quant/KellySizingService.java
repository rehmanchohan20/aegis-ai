package ai.aegis.quant;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class KellySizingService {
    public KellySizingResult calculate(BigDecimal accountBalance, BigDecimal winRate,
                                       BigDecimal averageWin, BigDecimal averageLoss) {
        if (accountBalance == null || accountBalance.signum() <= 0) {
            throw new IllegalArgumentException("accountBalance must be positive");
        }
        if (winRate == null || winRate.signum() < 0 || winRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("winRate must be between 0 and 1");
        }
        if (averageWin == null || averageWin.signum() <= 0 || averageLoss == null || averageLoss.signum() <= 0) {
            throw new IllegalArgumentException("averageWin and averageLoss must be positive");
        }

        BigDecimal b = averageWin.divide(averageLoss, 10, RoundingMode.HALF_UP);
        BigDecimal lossRate = BigDecimal.ONE.subtract(winRate);
        BigDecimal rawKelly = winRate.subtract(lossRate.divide(b, 10, RoundingMode.HALF_UP));
        BigDecimal halfKelly = rawKelly.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
        BigDecimal capped = halfKelly.min(BigDecimal.valueOf(0.02));

        List<String> warnings = new ArrayList<>();
        if (rawKelly.signum() <= 0) warnings.add("Estimated edge is non-positive; no capital should be risked.");
        if (halfKelly.compareTo(BigDecimal.valueOf(0.02)) > 0) warnings.add("Half-Kelly exceeded the platform safety cap and was limited to 2%.");
        warnings.add("Kelly sizing is highly sensitive to estimation error and must use a sufficiently large trade sample.");

        return new KellySizingResult(
                rawKelly.setScale(6, RoundingMode.HALF_UP),
                halfKelly.setScale(6, RoundingMode.HALF_UP),
                capped.setScale(6, RoundingMode.HALF_UP),
                accountBalance.multiply(capped).setScale(2, RoundingMode.HALF_UP),
                List.copyOf(warnings));
    }
}
