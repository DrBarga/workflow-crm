package dev.workflowcrm.contacts.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContactPhoneTest {
    private final ContactPhone phones = new ContactPhone("UA");

    @Test
    void normalizesLocalInternationalAndFormattedPhoneNumbers() {
        for (var input : java.util.List.of(
                "0671234567", "+380 (67) 123-45-67", "00 380 67 123 45 67", "+380\u00a067\u200e1234567")) {
            assertThat(phones.normalize(input)).isEqualTo("+380671234567");
        }
        assertThat(phones.normalize("+49 (30) 1234-5678")).isEqualTo("+493012345678");
        assertThat(phones.normalize(" ")).isNull();
        assertThat(phones.searchKeys("+380671234567")).contains("380671234567", "0671234567");
        assertThat(phones.searchKeys("old invalid number")).isEmpty();
    }

    @Test
    void rejectsInvalidNumbersExtensionsAndUnsupportedRegion() {
        for (var input : java.util.List.of("123", "abc", "+380671234567 ext 5", "+380+671234567", "9".repeat(65))) {
            assertThatIllegalArgumentException().isThrownBy(() -> phones.normalize(input));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactPhone("INVALID"));
    }
}
