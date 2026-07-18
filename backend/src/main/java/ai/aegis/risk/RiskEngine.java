package ai.aegis.risk;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RiskEngine {
    public RiskPlan build(String decision, BigDecimal entry, BigDecimal atr) {
        if (entry == null || atr == null || entry.signum() <= 0 || atr.signum() <= 0 || "WAIT".equals(decision)) {
            return new RiskPlan(entry, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRADE");
        }

        BigDecimal stopDistance = atr.multiply(BigDecimal.valueOf(1.5));
        BigDecimal stop;
        BigDecimal tp1;
        BigDecimal tp2;
        if ("LONG".equals(decision)) {
            stop = entry.subtract(stopDistance);
            tp1 = entry.add(stopDistance.multiply(BigDecimal.valueOf(2)));
            tp2 = entry.add(stopDistance.multiply(BigDecimal.valueOf(3)));
        } else {
            stop = entry.add(stopDistance);
            tp1 = entry.subtract(stopDistance.multiply(BigDecimal.valueOf(2)));
            tp2 = entry.subtract(stopDistance.multiply(BigDecimal.valueOf(3)));
        }

        BigDecimal riskPercent = stopDistance.multiply(BigDecimal.valueOf(100))
                .divide(entry, 4, RoundingMode.HALF_UP);
        return new RiskPlan(
                entry.setScale(4, RoundingMode.HALF_UP),
                stop.setScale(4, RoundingMode.HALF_UP),
                tp1.setScale(4, RoundingMode.HALF_UP),
                tp2.setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2),
                riskPercent,
                riskPercent.compareTo(BigDecimal.valueOf(3)) > 0 ? "HIGH_RISK" : "VALID"
        );
    }
}
