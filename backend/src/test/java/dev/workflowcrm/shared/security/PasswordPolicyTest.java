package dev.workflowcrm.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Ab1!short",
                "lowercase123!",
                "UPPERCASE123!",
                "WithoutDigits!",
                "WithoutSymbols12",
                "Password123!",
                "P@ssw0rd123!",
                "Qwerty!23456",
                "Admin!9283746",
                "workflow!9283746",
                "W0rkfl0w!9283746",
                "Bohdan!928374",
                "Zelenskyi!2837",
                "bohdan@example.com!9A"
            })
    void rejectsWeakOrAccountRelatedPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, "bohdan@example.com", "Bohdan Zelenskyi"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void acceptsAnUnrelatedPasswordWithEveryRequiredCharacterGroup() {
        assertThatCode(() -> PasswordPolicy.validate("N7!mK4#qR9@x", "bohdan@example.com", "Bohdan Zelenskyi"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNamesRegardlessOfCaseOrSeparators() {
        assertThatThrownBy(() -> PasswordPolicy.validate("БоГ-Дан!kA928374", "b@example.com", "Богдан Зеленьський"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void rejectsShortNames() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Li!rK9283746", "account@example.com", "Li Chan"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void rejectsPasswordsBeyondTheEncoderByteLimit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("A1!" + "ж".repeat(35), "b@example.com", "Test Name"))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
