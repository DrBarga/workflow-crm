package dev.workflowcrm.objects.domain;

import java.math.BigDecimal;

public record ObjectMapBounds(BigDecimal west, BigDecimal south, BigDecimal east, BigDecimal north) {
    public ObjectMapBounds {
        if (!coordinate(west, 180)
                || !coordinate(east, 180)
                || !coordinate(south, 90)
                || !coordinate(north, 90)
                || west.compareTo(east) >= 0
                || south.compareTo(north) >= 0) throw new IllegalArgumentException("Invalid WGS84 bounding box");
    }

    private static boolean coordinate(BigDecimal value, int maximum) {
        return value != null
                && value.precision() <= 32
                && value.scale() >= -32
                && value.scale() <= 32
                && value.abs().compareTo(BigDecimal.valueOf(maximum)) <= 0;
    }
}
