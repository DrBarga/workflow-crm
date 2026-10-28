package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.*;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.FieldViolation;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ObjectEditParser {
    private final ObjectMapper json;
    private final Validator validator;

    ObjectEditParser(ObjectMapper json, Validator validator) {
        this.json = json.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        this.validator = validator;
    }

    ObjectPatchCommand patch(JsonNode body) {
        var fields = fields(body);
        var request = read(body, ObjectPatchRequest.class);
        fields.remove("version");
        fields.remove("confirmationToken");
        if (fields.isEmpty()) throw invalid("body", "Provide at least one field to edit");
        for (String required : Set.of("name", "contactId", "contractTypeId"))
            if (fields.contains(required)
                    && (body.path(required).isNull()
                            || body.path(required).isTextual()
                                    && body.path(required).asText().isBlank()))
                throw invalid(required, "Field cannot be empty");
        return request.command(fields);
    }

    EntranceCoordinates point(JsonNode body) {
        var fields = fields(body);
        for (String field : fields)
            if (!Set.of("longitude", "latitude").contains(field)) throw invalid(field, "Unknown field");
        var point = read(body, CreateObjectRequest.Entrance.class);
        return new EntranceCoordinates(point.longitude(), point.latitude());
    }

    void empty(JsonNode body) {
        if (!fields(body).isEmpty()) throw invalid("body", "Provide an empty JSON object");
    }

    private Set<String> fields(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("body", "Provide a JSON object");
        var result = new LinkedHashSet<String>();
        body.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private <T> T read(JsonNode body, Class<T> type) {
        T request;
        try {
            request = json.treeToValue(body, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String field = e instanceof JsonMappingException mapping
                    ? mapping.getPath().stream()
                            .map(JsonMappingException.Reference::getFieldName)
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.joining("."))
                    : "body";
            throw invalid(field, "Invalid or protected field");
        }
        var errors = validator.validate(request).stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        return request;
    }

    private UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
