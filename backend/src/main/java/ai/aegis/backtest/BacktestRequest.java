package ai.aegis.backtest;

import ai.aegis.market.Candle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record BacktestRequest(
        @NotNull @DecimalMin("10.00") BigDecimal initialBalance,
        @NotNull @DecimalMin("0.10") @DecimalMax("2.00") BigDecimal riskPercent,
        @NotNull @Valid @Size(min = 60, max = 10000) List<Candle> candles
) {
}
