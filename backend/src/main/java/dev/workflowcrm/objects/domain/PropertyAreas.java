package dev.workflowcrm.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class PropertyAreas {
    @Column(name = "object_area", precision = 14, scale = 2)
    private BigDecimal objectArea;

    @Column(name = "land_area", precision = 14, scale = 2)
    private BigDecimal landArea;

    protected PropertyAreas() {}

    public PropertyAreas(BigDecimal objectArea, BigDecimal landArea) {
        if (objectArea == null && landArea == null) {
            throw new IllegalArgumentException("Object area or land area is required");
        }
        this.objectArea = area(objectArea);
        this.landArea = area(landArea);
    }

    private static BigDecimal area(BigDecimal area) {
        if (area != null
                && (area.signum() <= 0
                        || area.compareTo(new BigDecimal("999999999999.99")) > 0
                        || area.stripTrailingZeros().scale() > 2)) {
            throw new IllegalArgumentException(
                    "Area must be positive, within numeric(14,2), with at most two decimal places");
        }
        return area;
    }

    public BigDecimal getObjectArea() {
        return objectArea;
    }

    public BigDecimal getLandArea() {
        return landArea;
    }
}
