package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.contacts.api.ContactDetails;
import java.util.UUID;

record ContactFields(String name, String role, String phone, String email, UUID typeId, String comment) {
    ContactFields(ContactDetails details, UUID typeId, String comment) {
        this(details.name(), details.role(), details.phone(), details.email(), typeId, comment);
    }

    ContactFields {
        name = required(name, 200);
        role = required(role, 100);
        phone = optional(phone, 64);
        email = optional(email, 320);
        comment = optional(comment, 2000);
        if (phone == null && email == null) throw new IllegalArgumentException("Contact channel is required");
    }

    private static String required(String value, int limit) {
        String text = optional(value, limit);
        if (text == null) throw new IllegalArgumentException("Contact name and role are required");
        return text;
    }

    static String optional(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String text = value.strip();
        if (text.length() > limit) throw new IllegalArgumentException("Contact text is too long");
        return text;
    }
}
