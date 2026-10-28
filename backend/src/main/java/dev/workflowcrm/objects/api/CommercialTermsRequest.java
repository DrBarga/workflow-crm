package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.CommercialTerms;
import dev.workflowcrm.objects.domain.CounterpartyTaxStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

record CommercialTermsRequest(
        @Size(max = 5000) String leaseTerms,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal fixedRentAmount,
        @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal rentIndexationPercent,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal turnoverPaymentPercent,
        CounterpartyTaxStatus counterpartyTaxStatus,
        @PositiveOrZero Integer constructionTermDays,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal contractDurationYears) {
    CommercialTermsRequest {
        leaseTerms = leaseTerms == null || leaseTerms.isBlank() ? null : leaseTerms.strip();
        fixedRentAmount = number(fixedRentAmount);
        rentIndexationPercent = number(rentIndexationPercent);
        turnoverPaymentPercent = number(turnoverPaymentPercent);
        contractDurationYears = number(contractDurationYears);
    }

    CommercialTerms value() {
        return new CommercialTerms(
                leaseTerms,
                fixedRentAmount,
                rentIndexationPercent,
                turnoverPaymentPercent,
                counterpartyTaxStatus,
                constructionTermDays,
                contractDurationYears);
    }

    private static BigDecimal number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
