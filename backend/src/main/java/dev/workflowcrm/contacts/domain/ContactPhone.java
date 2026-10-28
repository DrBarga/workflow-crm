package dev.workflowcrm.contacts.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContactPhone {
    private final PhoneNumberUtil numbers = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    public ContactPhone(@Value("${app.contacts.default-phone-region:UA}") String defaultRegion) {
        this.defaultRegion = defaultRegion.toUpperCase(Locale.ROOT);
        if (!numbers.getSupportedRegions().contains(this.defaultRegion))
            throw new IllegalArgumentException("Unsupported default contact phone region");
    }

    public String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 64 || !value.matches("[+0-9\\s\\p{Z}\\p{Cf}().-]+")) throw invalid();
        String input = value.strip().replaceAll("[\\s\\p{Z}\\p{Cf}().-]", "");
        if (input.startsWith("00")) input = "+" + input.substring(2);
        if (!input.matches("\\+?[0-9]+")) throw invalid();
        try {
            var number = numbers.parse(input, defaultRegion);
            if (number.hasExtension() || !numbers.isValidNumber(number)) throw invalid();
            String result = numbers.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
            if (!result.matches("\\+[1-9][0-9]{9,14}")) throw invalid();
            return result;
        } catch (NumberParseException exception) {
            throw invalid();
        }
    }

    public List<String> searchKeys(String value) {
        String normalized;
        try {
            normalized = normalize(value);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        if (normalized == null) return List.of();
        try {
            var number = numbers.parse(normalized, defaultRegion);
            String digits = normalized.substring(1);
            if (number.getCountryCode() != numbers.getCountryCodeForRegion(defaultRegion)) return List.of(digits);
            String local = numbers.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                    .replaceAll("[^0-9]", "");
            return List.of(digits, local).stream().distinct().toList();
        } catch (NumberParseException exception) {
            throw new IllegalStateException("Normalized phone cannot be parsed", exception);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Provide a valid phone number");
    }
}
