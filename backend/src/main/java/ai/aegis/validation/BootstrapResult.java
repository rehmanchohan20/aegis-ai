package ai.aegis.validation;

import java.math.BigDecimal;
import java.util.List;

public record BootstrapResult(
        BigDecimal observedMean,
        BigDecimal lower95,
        BigDecimal upper95,
        BigDecimal probabilityMeanPositive,
        boolean statisticallyPromising,
        List<String> warnings
) {}
