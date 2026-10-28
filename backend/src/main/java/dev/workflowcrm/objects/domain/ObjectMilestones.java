package dev.workflowcrm.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
public record ObjectMilestones(
        @Column(name = "contract_signed_date") LocalDate contractSignedDate,
        @Column(name = "construction_acceptance_planned_date") LocalDate constructionAcceptancePlannedDate,
        @Column(name = "construction_acceptance_actual_date") LocalDate constructionAcceptanceActualDate,
        @Column(name = "opening_planned_date") LocalDate openingPlannedDate,
        @Column(name = "opening_date") LocalDate openingActualDate,
        @Column(name = "payment_act_planned_date") LocalDate paymentActPlannedDate,
        @Column(name = "payment_act_actual_date") LocalDate paymentActActualDate,
        @Column(name = "preliminary_contract_signed_date") LocalDate preliminaryContractSignedDate,
        @Column(name = "commissioning_date") LocalDate commissioningDate) {
    public ObjectMilestones(
            LocalDate signed,
            LocalDate acceptancePlan,
            LocalDate acceptanceActual,
            LocalDate openingPlan,
            LocalDate openingActual,
            LocalDate paymentPlan,
            LocalDate paymentActual) {
        this(
                signed,
                acceptancePlan,
                acceptanceActual,
                openingPlan,
                openingActual,
                paymentPlan,
                paymentActual,
                null,
                null);
    }

    public ObjectMilestones calculated(Integer constructionDays) {
        LocalDate planned = constructionAcceptancePlannedDate == null || constructionDays == null
                ? null
                : constructionAcceptancePlannedDate.plusDays(constructionDays);
        return new ObjectMilestones(
                contractSignedDate,
                constructionAcceptancePlannedDate,
                constructionAcceptanceActualDate,
                planned,
                openingActualDate,
                paymentActPlannedDate,
                paymentActActualDate,
                preliminaryContractSignedDate,
                commissioningDate);
    }

    public ObjectMilestones {
        if (openingActualDate != null && (contractSignedDate == null || openingActualDate.isBefore(contractSignedDate)))
            throw new IllegalArgumentException("Actual opening must follow contract signing");
    }
}
