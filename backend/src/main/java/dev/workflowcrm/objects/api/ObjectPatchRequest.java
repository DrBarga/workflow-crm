package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectEditValues;
import dev.workflowcrm.objects.domain.ObjectPatchCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

record ObjectPatchRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 500) String name,
        @Size(max = 500) String address,
        @Size(max = 64) String objectType,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal objectArea,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal landArea,
        UUID contactId,
        @Valid TechnicalSpecificationsRequest technicalSpecifications,
        @Valid CommercialTermsRequest commercialTerms,
        @Size(max = 5000) String technicalDescription,
        @Size(max = 5000) String commercialDescription,
        UUID contractTypeId,
        LocalDate contractSignedDate,
        LocalDate constructionAcceptancePlannedDate,
        LocalDate constructionAcceptanceActualDate,
        LocalDate openingPlannedDate,
        LocalDate openingActualDate,
        LocalDate paymentActPlannedDate,
        LocalDate paymentActActualDate,
        UUID dataSourceId,
        LocalDate preliminaryContractSignedDate,
        LocalDate commissioningDate,
        @Size(max = 200) String shortComment,
        @Pattern(regexp = "[a-f0-9]{64}") String confirmationToken) {
    ObjectPatchCommand command(Set<String> fields) {
        return new ObjectPatchCommand(
                version,
                fields,
                new ObjectEditValues(
                        name,
                        address,
                        objectType,
                        objectArea,
                        landArea,
                        contactId,
                        technicalSpecifications == null ? null : technicalSpecifications.value(),
                        commercialTerms == null ? null : commercialTerms.value(),
                        technicalDescription,
                        commercialDescription,
                        contractTypeId,
                        contractSignedDate,
                        constructionAcceptancePlannedDate,
                        constructionAcceptanceActualDate,
                        openingPlannedDate,
                        openingActualDate,
                        paymentActPlannedDate,
                        paymentActActualDate,
                        dataSourceId,
                        preliminaryContractSignedDate,
                        commissioningDate,
                        shortComment),
                confirmationToken);
    }
}
