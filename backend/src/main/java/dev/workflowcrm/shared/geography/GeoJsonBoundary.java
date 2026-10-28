package dev.workflowcrm.shared.geography;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class GeoJsonBoundary {
    private final List<JsonNode> polygons = new ArrayList<>();

    public GeoJsonBoundary(JsonNode geometry) {
        var coordinates = geometry.path("coordinates");
        if (!coordinates.isArray() || coordinates.isEmpty()) throw invalid();
        switch (geometry.path("type").asText()) {
            case "Polygon" -> polygons.add(coordinates);
            case "MultiPolygon" -> coordinates.forEach(polygons::add);
            default -> throw invalid();
        }
        for (var polygon : polygons) {
            if (!polygon.isArray() || polygon.isEmpty()) throw invalid();
            for (var ring : polygon) {
                if (!ring.isArray() || ring.size() < 4 || !ring.get(0).equals(ring.get(ring.size() - 1)))
                    throw invalid();
                for (var point : ring) {
                    if (!point.isArray()
                            || point.size() < 2
                            || !point.get(0).isNumber()
                            || !point.get(1).isNumber()) throw invalid();
                    double x = point.get(0).asDouble(), y = point.get(1).asDouble();
                    if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 180 || Math.abs(y) > 90)
                        throw invalid();
                }
            }
        }
    }

    public boolean contains(double longitude, double latitude) {
        for (var polygon : polygons) {
            if (!inRing(polygon.get(0), longitude, latitude)) continue;
            boolean hole = false;
            for (int i = 1; i < polygon.size(); i++) if (inRing(polygon.get(i), longitude, latitude)) hole = true;
            if (!hole) return true;
        }
        return false;
    }

    private static boolean inRing(JsonNode ring, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            double ax = ring.get(i).get(0).asDouble(), ay = ring.get(i).get(1).asDouble();
            double bx = ring.get(j).get(0).asDouble(), by = ring.get(j).get(1).asDouble();
            double cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax);
            if (Math.abs(cross) < 1e-12
                    && x >= Math.min(ax, bx)
                    && x <= Math.max(ax, bx)
                    && y >= Math.min(ay, by)
                    && y <= Math.max(ay, by)) return true;
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside;
        }
        return inside;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid region polygon geometry");
    }
}
