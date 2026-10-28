package dev.workflowcrm.objects.domain;

final class ObjectText {
    private ObjectText() {}

    static String required(String value, int length, String field) {
        String text = optional(value, length, field);
        if (text == null) throw new IllegalArgumentException(field + " is required");
        return text;
    }

    static String optional(String value, int length, String field) {
        if (value == null || value.isBlank()) return null;
        String text = value.strip();
        if (text.codePointCount(0, text.length()) > length)
            throw new IllegalArgumentException(field + " exceeds " + length + " characters");
        return text;
    }
}
