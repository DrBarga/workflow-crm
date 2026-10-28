package dev.workflowcrm.contacts.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.contacts.domain.*;
import dev.workflowcrm.shared.error.FieldViolation;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ContactRequestParser {
    private static final Set<String> CREATE =
            Set.of("name", "role", "phone", "email", "comment", "contactTypeId", "objectIds");
    private static final Set<String> PATCH =
            Set.of("version", "name", "role", "phone", "email", "comment", "contactTypeId");
    private final ContactRequestReader reader;
    private final Validator validator;
    private final ContactPhone phones;

    ContactRequestParser(ContactRequestReader reader, Validator validator, ContactPhone phones) {
        this.reader = reader;
        this.validator = validator;
        this.phones = phones;
    }

    CreateContact create(JsonNode body) {
        var parsed = parse(body, true);
        var r = parsed.request();
        return new CreateContact(r.details(parsed.phone()), r.contactTypeId(), r.comment(), r.objectIds());
    }

    ContactPatch patch(JsonNode body) {
        var parsed = parse(body, false);
        var fields = new LinkedHashSet<String>();
        body.fieldNames().forEachRemaining(fields::add);
        fields.remove("version");
        if (fields.isEmpty()) throw new UnprocessableInputException("body", "Provide at least one field to edit");
        var r = parsed.request();
        return new ContactPatch(r.version(), fields, r.details(parsed.phone()), r.contactTypeId(), r.comment());
    }

    void deletion(long version, String token) {
        if (version < 0) throw new UnprocessableInputException("version", "Version must not be negative");
        if (token != null && !token.matches("[0-9a-f]{64}"))
            throw new UnprocessableInputException("confirmationToken", "Invalid confirmation token");
    }

    private Parsed parse(JsonNode body, boolean create) {
        var r = reader.read(body, create ? CREATE : PATCH);
        var errors = new ArrayList<FieldViolation>();
        validator
                .validate(r, create ? ContactWriteRequest.Create.class : ContactWriteRequest.Patch.class)
                .forEach(v -> errors.add(new FieldViolation(v.getPropertyPath().toString(), v.getMessage())));
        if (!create) {
            if (body.has("name") && r.name() == null) errors.add(new FieldViolation("name", "Name is required"));
            if (body.has("role") && r.role() == null) errors.add(new FieldViolation("role", "Role is required"));
        }
        if (new HashSet<>(r.objectIds()).size() != r.objectIds().size())
            errors.add(new FieldViolation("objectIds", "Object IDs must not repeat"));
        String phone = null;
        try {
            phone = phones.normalize(r.phone());
        } catch (IllegalArgumentException exception) {
            errors.add(new FieldViolation("phone", "Provide a valid phone number"));
        }
        if (create && r.phone() == null && r.email() == null)
            errors.add(new FieldViolation("contact", "Provide phone or email for the contact"));
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        return new Parsed(r, phone);
    }

    private record Parsed(ContactWriteRequest request, String phone) {}
}
