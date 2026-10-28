package dev.workflowcrm.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public class EntranceCoordinates {
    @Column(name = "entrance_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "entrance_latitude", nullable = false, precision = 9, scale = 7)
    private BigDecimal latitude;

    protected EntranceCoordinates() {}

    public EntranceCoordinates(BigDecimal longitude, BigDecimal latitude) {
        this.longitude = coordinate(longitude, 180, "Longitude");
        this.latitude = coordinate(latitude, 90, "Latitude");
    }

    private static BigDecimal coordinate(BigDecimal coordinate, int limit, String name) {
        Objects.requireNonNull(coordinate, name + " is required");
        if (coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0
                || coordinate.stripTrailingZeros().scale() > 7) {
            throw new IllegalArgumentException(name + " must be within WGS84 bounds with at most seven decimal places");
        }
        return coordinate;
    }

    public String googleMapsUrl() {
        return "https://www.google.com/maps/search/?api=1&query=" + latitude.toPlainString() + "%2C"
                + longitude.toPlainString();
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }
}
