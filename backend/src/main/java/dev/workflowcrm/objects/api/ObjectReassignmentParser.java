package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectReassignmentParser {
    record Command(UUID managerId, long version) {}

    Command parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("body", "Provide a JSON object");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("managerId", "version").contains(field)) throw invalid(field, "Unknown field");
        });
        var version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw invalid("version", "Provide a non-negative integer version");
        var manager = body.path("managerId");
        if (!manager.isTextual()) throw invalid("managerId", "Provide a manager UUID");
        try {
            UUID id = UUID.fromString(manager.textValue());
            if (!id.toString().equalsIgnoreCase(manager.textValue())) throw new IllegalArgumentException();
            return new Command(id, version.longValue());
        } catch (IllegalArgumentException e) {
            throw invalid("managerId", "Provide a manager UUID");
        }
    }

    private UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
