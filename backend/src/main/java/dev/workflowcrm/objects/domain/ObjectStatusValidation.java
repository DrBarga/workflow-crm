package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusValidation {
    private final ObjectStatusStore store;
    private final dev.workflowcrm.tasks.api.TransitionTaskRequirements tasks;

    ObjectStatusValidation(ObjectStatusStore store, dev.workflowcrm.tasks.api.TransitionTaskRequirements tasks) {
        this.store = store;
        this.tasks = tasks;
    }

    ObjectStatusValues values(RealEstateObject object, ObjectStatusTransition rule, ObjectStatusCommand command) {
        if (command.contractSignedDate() != null
                && !rule.getRequiredFields().contains(TransitionRequiredField.CONTRACT_SIGNED_DATE))
            throw invalid("contractSignedDate", "This transition does not accept a signing date");
        if (command.openingDate() != null && !rule.getRequiredFields().contains(TransitionRequiredField.OPENING_DATE))
            throw invalid("openingDate", "This transition does not accept an actual opening date");
        if (command.contractTypeId() != null
                && !rule.getRequiredFields().contains(TransitionRequiredField.CONTRACT_TYPE))
            throw invalid("contractTypeId", "This transition does not accept a contract type");
        RefusalReason reason = command.refusalReasonId() == null
                ? object.getRefusalReason()
                : store.refusalReason(command.refusalReasonId());
        ContractType type = command.contractTypeId() == null
                ? object.getSpecifications() == null
                        ? null
                        : object.getSpecifications().getContractTypeReference()
                : store.contractType(command.contractTypeId());
        if (command.signing() != null
                && !rule.getRequiredFields().contains(TransitionRequiredField.CONTRACT_SIGNED_DATE))
            throw invalid("commissioningDate", "Signing details are not accepted for this transition");
        var old = object.getMilestones();
        var signed =
                command.contractSignedDate() == null ? object.getContractSignedDate() : command.contractSignedDate();
        var opened = command.openingDate() == null ? object.getOpeningDate() : command.openingDate();
        if (command.refusalReasonId() != null
                && !rule.getRequiredFields().contains(TransitionRequiredField.REFUSAL_REASON))
            throw invalid("refusalReasonId", "This transition does not accept a refusal reason");
        if (rule.getEffects().signingReset()) {
            signed = null;
            opened = null;
        }
        if (rule.getEffects().openingReset()) opened = null;
        if (opened != null && (signed == null || opened.isBefore(signed)))
            throw invalid("openingDate", "Opening must follow contract signing");
        var commercial = object.getSpecifications() == null
                ? null
                : object.getSpecifications().getCommercial();
        var details = command.signing();
        var preliminary = old == null ? null : old.preliminaryContractSignedDate();
        var commissioning = old == null ? null : old.commissioningDate();
        var acceptance = old == null ? null : old.constructionAcceptancePlannedDate();
        Integer days = commercial == null ? null : commercial.constructionTermDays();
        if (details != null) {
            preliminary = details.value(
                    "preliminaryContractSignedDate", details.preliminaryContractSignedDate(), preliminary);
            commissioning = details.value("commissioningDate", details.commissioningDate(), commissioning);
            acceptance = details.value(
                    "constructionAcceptancePlannedDate", details.constructionAcceptancePlannedDate(), acceptance);
            days = details.value("constructionTermDays", details.constructionTermDays(), days);
            commercial = commercial == null
                    ? new CommercialTerms(null, null, null, null, null, days, null)
                    : new CommercialTerms(
                            commercial.leaseTerms(),
                            commercial.fixedRentAmount(),
                            commercial.rentIndexationPercent(),
                            commercial.turnoverPaymentPercent(),
                            commercial.counterpartyTaxStatus(),
                            days,
                            commercial.contractDurationYears());
        }
        var milestones = new ObjectMilestones(
                        signed,
                        acceptance,
                        old == null ? null : old.constructionAcceptanceActualDate(),
                        null,
                        opened,
                        old == null ? null : old.paymentActPlannedDate(),
                        old == null ? null : old.paymentActActualDate(),
                        preliminary,
                        commissioning)
                .calculated(days);
        if (command.plannedDateProvided()
                && !java.util.Objects.equals(command.openingPlannedDate(), milestones.openingPlannedDate()))
            throw invalid("openingPlannedDate", "Opening plan is calculated from construction acceptance and term");
        var values = new ObjectStatusValues(reason, type, milestones, commercial);
        var missingFields = ObjectTransitionFields.missing(object, rule, values, command.refusalReasonId() != null);
        var assessment = tasks.requirements(object.getId(), rule.getFromStatus().getId(), rule.getRequiredTaskCodes());
        var conditions = ObjectTransitionConditions.of(missingFields, rule.getRequiredTaskCodes(), assessment);
        if (!conditions.fulfilled()) throw new ObjectTransitionConditionsException(conditions, values);
        return values;
    }

    private static UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
