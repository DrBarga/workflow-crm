package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;

final class ObjectCreationName {
    private ObjectCreationName() {}

    static String resolve(String name, String address) {
        String value = normalize(name);
        if (value == null) value = normalize(address);
        if (value == null) throw new UnprocessableInputException("name", "Fill in the object name or address");
        if (value.codePointCount(0, value.length()) > 500)
            throw new UnprocessableInputException("name", "Object name or address must not exceed 500 characters");
        return value;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.replace('\u00a0', ' ').replace('\u202f', ' ').strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
