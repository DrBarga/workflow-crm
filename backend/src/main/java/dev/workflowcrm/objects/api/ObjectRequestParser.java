package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.shared.error.FieldViolation;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
class ObjectRequestParser {
    private final ObjectMapper json;
    private final Validator validator;

    ObjectRequestParser(ObjectMapper json, Validator validator) {
        this.json = json.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        this.validator = validator;
    }

    CreateObjectRequest parse(JsonNode body) {
        var errors = new ArrayList<FieldViolation>();
        ObjectCreationFields.check(body, errors);
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        CreateObjectRequest request;
        try {
            request = json.treeToValue(body, CreateObjectRequest.class);
        } catch (JsonProcessingException exception) {
            String field = exception instanceof JsonMappingException mapping
                    ? mapping.getPath().stream()
                            .map(JsonMappingException.Reference::getFieldName)
                            .filter(java.util.Objects::nonNull)
                            .collect(java.util.stream.Collectors.joining("."))
                    : "body";
            throw new UnprocessableInputException(field, "Invalid field type or value");
        }
        validator
                .validate(request)
                .forEach(v -> errors.add(new FieldViolation(v.getPropertyPath().toString(), v.getMessage())));
        if (request.objectArea() == null && request.landArea() == null)
            errors.add(new FieldViolation("objectArea", "Provide objectArea or landArea"));
        if (request.contact() != null && request.contactId() != null)
            errors.add(new FieldViolation("contactId", "Choose contactId or a new contact, not both"));
        if (request.contact() != null
                && request.contact().phone() == null
                && request.contact().email() == null)
            errors.add(new FieldViolation("contact", "Provide phone or email for the contact"));
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        return request;
    }

    String hash(CreateObjectRequest request) {
        return ObjectRequestFingerprint.hash(request, json);
    }
}
