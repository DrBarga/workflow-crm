package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;

final class ObjectCommentText {
    static final int LIMIT = 1000;

    private ObjectCommentText() {}

    static String require(String text) {
        if (text == null
                || text.codePoints()
                        .allMatch(c ->
                                Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x200B || c == 0xFEFF))
            throw new UnprocessableInputException("text", "Comment cannot be empty");
        if (text.codePointCount(0, text.length()) > LIMIT)
            throw new UnprocessableInputException("text", "Comment cannot exceed 1000 characters");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))) throw invalid();
            }
        }
        return text;
    }

    private static UnprocessableInputException invalid() {
        return new UnprocessableInputException("text", "Comment must contain valid Unicode text");
    }
}
