package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.workflowcrm.objects.domain.CreateObjectCommand;
import dev.workflowcrm.objects.domain.EntranceCoordinates;
import dev.workflowcrm.objects.domain.ObjectContact;
import dev.workflowcrm.objects.domain.PropertyAreas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

record CreateObjectRequest(
        @Size(max = 500) String name,
        @Size(max = 64) String objectType,
        @Size(max = 500) String address,
        UUID regionId,
        @NotNull @Valid Entrance entrance,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal objectArea,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal landArea,
        @Valid Contact contact,
        UUID contactId,
        UUID managerId,
        @Valid TechnicalSpecificationsRequest technicalSpecifications,
        @Valid CommercialTermsRequest commercialTerms,
        UUID dataSourceId,
        @Size(max = 200) String shortComment,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) Boolean confirmDuplicates) {
    CreateObjectRequest {
        name = trim(name == null ? null : name.replace('\u00a0', ' ').replace('\u202f', ' '));
        objectType = trim(objectType);
        address = trim(address);
        shortComment = trim(shortComment);
        objectArea = number(objectArea);
        landArea = number(landArea);
    }

    record Entrance(
            @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7) BigDecimal latitude) {
        Entrance {
            longitude = number(longitude);
            latitude = number(latitude);
        }
    }

    record Contact(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String role,
            @Pattern(regexp = "\\+[1-9][0-9]{9,14}") String phone,
            @Email @Size(max = 254) String email) {
        Contact {
            name = trim(name);
            role = trim(role);
            email = trim(email);
            if (email != null) email = email.toLowerCase(Locale.ROOT);
            phone = trim(phone);
            if (phone != null) phone = phone.replaceAll("[\\s()\\-]", "");
        }
    }

    CreateObjectCommand command() {
        return new CreateObjectCommand(
                name,
                objectType,
                address,
                regionId,
                new EntranceCoordinates(entrance.longitude(), entrance.latitude()),
                new PropertyAreas(objectArea, landArea),
                contact == null
                        ? null
                        : new ObjectContact(contact.name(), contact.role(), contact.phone(), contact.email()),
                managerId,
                contactId,
                technicalSpecifications == null ? null : technicalSpecifications.value(),
                commercialTerms == null ? null : commercialTerms.value(),
                dataSourceId,
                shortComment);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
