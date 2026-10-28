package dev.workflowcrm.objects.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public record CommercialTerms(
        @Column(name = "lease_terms", columnDefinition = "text") String leaseTerms,
        @Column(name = "fixed_rent_amount", precision = 14, scale = 2) BigDecimal fixedRentAmount,
        @Column(name = "rent_indexation_percent", precision = 7, scale = 2) BigDecimal rentIndexationPercent,
        @Column(name = "turnover_payment_percent", precision = 5, scale = 2) BigDecimal turnoverPaymentPercent,
        @Enumerated(EnumType.STRING) @Column(name = "counterparty_tax_status", length = 40)
                CounterpartyTaxStatus counterpartyTaxStatus,
        @Column(name = "construction_term_days") Integer constructionTermDays,
        @Column(name = "contract_duration_years", precision = 5, scale = 2) BigDecimal contractDurationYears) {
    public CommercialTerms {
        leaseTerms = ObjectText.optional(leaseTerms, Integer.MAX_VALUE, "Lease terms");
        fixedRentAmount = ObjectNumbers.nonnegative(fixedRentAmount, 14, 2, "Fixed rent");
        rentIndexationPercent = ObjectNumbers.nonnegative(rentIndexationPercent, 7, 2, "Rent indexation");
        turnoverPaymentPercent = ObjectNumbers.nonnegative(turnoverPaymentPercent, 5, 2, "Turnover payment");
        if (turnoverPaymentPercent != null && turnoverPaymentPercent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Turnover payment must not exceed 100 percent");
        constructionTermDays = ObjectNumbers.nonnegative(constructionTermDays, "Construction term");
        contractDurationYears = ObjectNumbers.nonnegative(contractDurationYears, 5, 2, "Contract duration");
    }
}
