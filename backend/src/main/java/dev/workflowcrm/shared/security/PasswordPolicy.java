package dev.workflowcrm.shared.security;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class PasswordPolicy {
    private static final List<String> COMMON_BASES = List.of(
            "password",
            "qwerty",
            "asdfgh",
            "zxcvbn",
            "123456",
            "654321",
            "123123",
            "111111",
            "abc123",
            "admin",
            "welcome",
            "letmein",
            "changeme",
            "iloveyou",
            "пароль");

    private PasswordPolicy() {}

    public static void validate(String password, String email, String fullName) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new InvalidPasswordException("Password must contain 12 to 128 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException("Password must not exceed 72 UTF-8 bytes");
        }
        if (password.chars().noneMatch(value -> value >= 'A' && value <= 'Z')) {
            throw new InvalidPasswordException("Password must include an uppercase Latin letter");
        }
        if (password.chars().noneMatch(value -> value >= 'a' && value <= 'z')) {
            throw new InvalidPasswordException("Password must include a lowercase Latin letter");
        }
        if (password.chars().noneMatch(value -> value >= '0' && value <= '9')) {
            throw new InvalidPasswordException("Password must include a digit");
        }
        if (password.chars().noneMatch(value -> "!@#$%^&*()_+-=".indexOf(value) >= 0)) {
            throw new InvalidPasswordException("Password must include a special character from !@#$%^&*()_+-=");
        }
        String normalized = normalize(password);
        if (COMMON_BASES.stream().map(PasswordPolicy::normalize).anyMatch(normalized::contains)) {
            throw new InvalidPasswordException("Password must not contain common passwords");
        }
        if (normalized.contains("workflow")) {
            throw new InvalidPasswordException("Password must not contain Workflow");
        }
        String localPart = email == null ? "" : email.split("@", 2)[0];
        if (Stream.concat(
                        Stream.of(email == null ? "" : email),
                        Stream.concat(parts(localPart), parts(fullName == null ? "" : fullName)))
                .map(PasswordPolicy::normalize)
                .filter(part -> part.length() >= 2)
                .anyMatch(normalized::contains)) {
            throw new InvalidPasswordException("Password must not contain your email or name");
        }
    }

    private static Stream<String> parts(String value) {
        return Stream.of(value.split("[^\\p{L}\\p{N}]+"));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('@', 'a')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('5', 's')
                .replace('7', 't')
                .replace('$', 's')
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
