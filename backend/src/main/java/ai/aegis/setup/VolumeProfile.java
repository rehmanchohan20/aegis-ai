package ai.aegis.setup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VolumeProfile(
        String symbol,
        String timeframe,
        String profileType,
        Instant rangeStart,
        Instant rangeEnd,
        BigDecimal rangeLow,
        BigDecimal rangeHigh,
        BigDecimal pointOfControl,
        BigDecimal valueAreaHigh,
        BigDecimal valueAreaLow,
        List<Node> highVolumeNodes,
        List<Node> lowVolumeNodes,
        List<Node> bins,
        List<DevelopingPoint> developingPoc,
        String volumeState,
        boolean rangeDetected,
        BigDecimal valueAreaVolumeFraction,
        String methodology
) {
    public VolumeProfile {
        highVolumeNodes = List.copyOf(highVolumeNodes == null ? List.of() : highVolumeNodes);
        lowVolumeNodes = List.copyOf(lowVolumeNodes == null ? List.of() : lowVolumeNodes);
        bins = List.copyOf(bins == null ? List.of() : bins);
        developingPoc = List.copyOf(developingPoc == null ? List.of() : developingPoc);
    }

    public record Node(BigDecimal lower, BigDecimal upper, BigDecimal midpoint,
                       BigDecimal volume, BigDecimal volumeFraction, String nodeType) { }
    public record DevelopingPoint(Instant time, BigDecimal price) { }
}
