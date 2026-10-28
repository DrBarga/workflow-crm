package dev.workflowcrm.contacts.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import dev.workflowcrm.shared.error.FieldViolation;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ContactRequestReader {
    private final ObjectMapper json;

    ContactRequestReader(ObjectMapper json) {
        this.json = json.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    ContactWriteRequest read(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("body");
        var errors = new ArrayList<FieldViolation>();
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) errors.add(new FieldViolation(field, "Invalid or protected field"));
            else if (!validType(field, body.get(field))) errors.add(new FieldViolation(field, "Invalid field type"));
        });
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        try {
            return json.treeToValue(body, ContactWriteRequest.class);
        } catch (JsonProcessingException exception) {
            String field = exception instanceof JsonMappingException mapping
                    ? mapping.getPath().stream()
                            .map(JsonMappingException.Reference::getFieldName)
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.joining("."))
                    : "body";
            throw invalid(field);
        }
    }

    private static UnprocessableInputException invalid(String field) {
        return new UnprocessableInputException(field, "Invalid or protected field");
    }

    private static boolean validType(String field, JsonNode value) {
        if (value.isNull()) return true;
        if (field.equals("version")) return value.isIntegralNumber() && value.canConvertToLong();
        if (!field.equals("objectIds")) return value.isTextual();
        if (!value.isArray()) return false;
        for (var item : value) if (!item.isNull() && !item.isTextual()) return false;
        return true;
    }
}
