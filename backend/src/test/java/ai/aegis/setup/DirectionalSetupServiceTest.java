package ai.aegis.setup;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionalSetupServiceTest {
    @Test
    void ignoresUnavailableStructureAndBreakoutAnchors() {
        BigDecimal result = DirectionalSetupService.nearestAnchor(BigDecimal.valueOf(100),
                Arrays.asList(BigDecimal.valueOf(90), BigDecimal.valueOf(99), null, null));
        assertEquals(0, BigDecimal.valueOf(99).compareTo(result));
    }
}
