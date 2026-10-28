package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectListFilter;
import dev.workflowcrm.objects.domain.ObjectMapBounds;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
class ObjectMapParser {
    private static final Set<String> FILTERS =
            Set.of("status", "managerId", "regionId", "objectType", "q", "createdFrom", "createdTo");
    private final ObjectListParser lists;

    ObjectMapParser(ObjectListParser lists) {
        this.lists = lists;
    }

    record Request(ObjectListFilter filter, ObjectMapBounds bounds) {}

    Request parse(MultiValueMap<String, String> values) {
        var filters = new LinkedMultiValueMap<String, String>();
        values.forEach((field, entries) -> {
            if (FILTERS.contains(field)) filters.put(field, entries);
            else if (!field.equals("bbox")) throw new UnprocessableInputException(field, "Invalid map parameter");
        });
        var bbox = values.get("bbox");
        if (bbox == null
                || bbox.size() != 1
                || bbox.getFirst() == null
                || bbox.getFirst().length() > 256) throw invalidBounds();
        ObjectMapBounds bounds;
        try {
            var coordinates = bbox.getFirst().split(",", -1);
            if (coordinates.length != 4) throw new IllegalArgumentException();
            bounds = new ObjectMapBounds(
                    coordinate(coordinates[0]),
                    coordinate(coordinates[1]),
                    coordinate(coordinates[2]),
                    coordinate(coordinates[3]));
        } catch (IllegalArgumentException error) {
            throw invalidBounds();
        }
        return new Request(lists.parse(filters).filter(), bounds);
    }

    private static BigDecimal coordinate(String value) {
        return new BigDecimal(value.strip());
    }

    private static UnprocessableInputException invalidBounds() {
        return new UnprocessableInputException(
                "bbox", "Expected west,south,east,north within WGS84 bounds; west < east and south < north");
    }
}
