package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectSigningAccess {
    private final ObjectStatusAccess access;
    private final ObjectFieldPolicy fields;
    private final ObjectTaskTransition tasks;

    ObjectSigningAccess(ObjectStatusAccess access, ObjectFieldPolicy fields, ObjectTaskTransition tasks) {
        this.access = access;
        this.fields = fields;
        this.tasks = tasks;
    }

    void require(UUID actor, RealEstateObject object, ObjectSigningDetails signing) {
        if (signing == null) return;
        var editable = fields.editable(access.require(actor, object), tasks.hasContractTypeTaskSet(object.getId()));
        var dates = object.getMilestones();
        var commercial = object.getSpecifications() == null
                ? null
                : object.getSpecifications().getCommercial();
        for (String field : signing.fields()) {
            Object current =
                    switch (field) {
                        case "preliminaryContractSignedDate" ->
                            dates == null ? null : dates.preliminaryContractSignedDate();
                        case "commissioningDate" -> dates == null ? null : dates.commissioningDate();
                        case "constructionAcceptancePlannedDate" ->
                            dates == null ? null : dates.constructionAcceptancePlannedDate();
                        case "constructionTermDays" -> commercial == null ? null : commercial.constructionTermDays();
                        default -> throw new UnprocessableInputException(field, "Unknown signing field");
                    };
            Object proposed =
                    switch (field) {
                        case "preliminaryContractSignedDate" -> signing.preliminaryContractSignedDate();
                        case "commissioningDate" -> signing.commissioningDate();
                        case "constructionAcceptancePlannedDate" -> signing.constructionAcceptancePlannedDate();
                        default -> signing.constructionTermDays();
                    };
            String permission = field.equals("constructionTermDays") ? "commercialTerms" : field;
            if (!Objects.equals(current, proposed) && !Boolean.TRUE.equals(editable.get(permission))) {
                if (field.equals("commissioningDate")) throw ObjectEditAccess.denied();
                throw new UnprocessableInputException(field, "Field cannot be edited by this role at this stage");
            }
        }
    }
}
