package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.EntranceCoordinates;
import dev.workflowcrm.objects.domain.UkraineBoundary;
import dev.workflowcrm.shared.geography.GeoJsonBoundary;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class GeoJsonUkraineBoundary implements UkraineBoundary {
    private final List<GeoJsonBoundary> boundaries = new ArrayList<>();

    public GeoJsonUkraineBoundary(ObjectMapper json) throws IOException {
        try (var input = new ClassPathResource("geography/ukraine.geojson").getInputStream()) {
            for (var feature : json.readTree(input).path("features"))
                boundaries.add(new GeoJsonBoundary(feature.path("geometry")));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Ukraine boundary geometry", exception);
        }
        if (boundaries.isEmpty()) throw new IOException("Ukraine boundary is empty");
    }

    public boolean contains(EntranceCoordinates coordinates) {
        return boundaries.stream()
                .anyMatch(b -> b.contains(
                        coordinates.getLongitude().doubleValue(),
                        coordinates.getLatitude().doubleValue()));
    }
}
