package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;

final class ObjectEditMilestones {
    private ObjectEditMilestones() {}

    static ObjectMilestones merge(RealEstateObject object, ObjectPatchCommand command) {
        var old = object.getMilestones();
        var v = command.values();
        var signed = command.value("contractSignedDate", v.contractSignedDate(), object.getContractSignedDate());
        var opened = command.value("openingActualDate", v.openingActualDate(), object.getOpeningDate());
        if (opened != null && (signed == null || opened.isBefore(signed)))
            throw new UnprocessableInputException("openingActualDate", "Opening must follow contract signing");
        return new ObjectMilestones(
                signed,
                command.value(
                        "constructionAcceptancePlannedDate",
                        v.constructionAcceptancePlannedDate(),
                        old == null ? null : old.constructionAcceptancePlannedDate()),
                command.value(
                        "constructionAcceptanceActualDate",
                        v.constructionAcceptanceActualDate(),
                        old == null ? null : old.constructionAcceptanceActualDate()),
                command.value(
                        "openingPlannedDate", v.openingPlannedDate(), old == null ? null : old.openingPlannedDate()),
                opened,
                command.value(
                        "paymentActPlannedDate",
                        v.paymentActPlannedDate(),
                        old == null ? null : old.paymentActPlannedDate()),
                command.value(
                        "paymentActActualDate",
                        v.paymentActActualDate(),
                        old == null ? null : old.paymentActActualDate()),
                command.value(
                        "preliminaryContractSignedDate",
                        v.preliminaryContractSignedDate(),
                        old == null ? null : old.preliminaryContractSignedDate()),
                command.value(
                        "commissioningDate", v.commissioningDate(), old == null ? null : old.commissioningDate()));
    }

    static void protect(RealEstateObject object, ObjectMilestones dates) {
        if (object.getContractSignedDate() != null && dates.contractSignedDate() == null)
            throw new UnprocessableInputException(
                    "contractSignedDate", "Clear this date through a backward status transition");
        if (object.getOpeningDate() != null && dates.openingActualDate() == null)
            throw new UnprocessableInputException(
                    "openingActualDate", "Clear this date through a backward status transition");
    }
}
