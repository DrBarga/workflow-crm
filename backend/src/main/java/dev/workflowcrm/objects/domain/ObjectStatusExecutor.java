package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ObjectStatusExecutor {
    enum PreviewMode {
        NONE,
        TEMPLATE,
        REQUIRED
    }

    private final ObjectStatusValidation validation;
    private final ObjectTaskTransition tasks;
    private final ObjectStatusFingerprint fingerprint;
    private final ObjectStatusWriter writer;
    private final ObjectStatusRequests requests;
    private final ObjectContractTypePolicy contractTypes;
    private final ObjectTaskTriggerDate triggerDate;
    private final ObjectSigningAccess signingAccess;

    ObjectStatusExecutor(
            ObjectStatusValidation validation,
            ObjectTaskTransition tasks,
            ObjectStatusFingerprint fingerprint,
            ObjectStatusWriter writer,
            ObjectStatusRequests requests,
            ObjectContractTypePolicy contractTypes,
            ObjectTaskTriggerDate triggerDate,
            ObjectSigningAccess signingAccess) {
        this.validation = validation;
        this.tasks = tasks;
        this.fingerprint = fingerprint;
        this.writer = writer;
        this.requests = requests;
        this.contractTypes = contractTypes;
        this.triggerDate = triggerDate;
        this.signingAccess = signingAccess;
    }

    ObjectStatusResult run(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            boolean approved,
            UUID requestId,
            UUID initiatorId) {
        return run(actor, object, rule, command, approved, requestId, initiatorId, o -> {}, false);
    }

    ObjectStatusResult run(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            boolean approved,
            UUID requestId,
            UUID initiatorId,
            java.util.function.Consumer<RealEstateObject> edit,
            boolean previewOnly) {
        return run(
                actor,
                object,
                rule,
                command,
                approved,
                requestId,
                initiatorId,
                edit,
                previewOnly ? PreviewMode.TEMPLATE : PreviewMode.NONE);
    }

    ObjectStatusResult run(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            boolean approved,
            UUID requestId,
            UUID initiatorId,
            java.util.function.Consumer<RealEstateObject> edit,
            PreviewMode preview) {
        contractTypes.require(initiatorId == null ? actor : initiatorId, object, command.contractTypeId());
        signingAccess.require(initiatorId == null ? actor : initiatorId, object, command.signing());
        var values = validation.values(object, rule, command);
        if (rule.isUnavailableAfterTaskCreation() && tasks.hasTasks(object.getId()))
            throw new UnprocessableInputException("status", "Tasks have already been created for this object");
        var context =
                ObjectStatusTaskContext.of(object, rule, command, values, triggerDate.today(), approved ? actor : null);
        if (rule.getUnfinishedTaskAction() == UnfinishedTaskAction.BLOCK) {
            var unfinished = tasks.unfinished(context);
            if (!unfinished.isEmpty()) throw new UnfinishedTasksException(unfinished);
        }
        if (rule.isConfirmationRequired() && !approved && preview == PreviewMode.NONE) {
            UUID request = requests.create(actor, object, rule, command);
            return result(false, object, request, null, List.of());
        }
        var plan = tasks.preview(context);
        boolean unresolved =
                plan.tasks().stream().anyMatch(task -> task.assignment().selectionRequired());
        if (unresolved && preview == PreviewMode.NONE)
            throw new UnprocessableInputException(
                    "assignees", "Choose an active assignee for every task before confirming");
        String token = fingerprint.token(actor, object, rule, command, plan);
        if (preview != PreviewMode.NONE)
            return result(
                    false,
                    object,
                    requestId,
                    !unresolved
                                    && (!rule.isConfirmationRequired() || approved)
                                    && (preview == PreviewMode.REQUIRED || plan.templateId() != null)
                            ? token
                            : null,
                    plan.tasks());
        if (plan.templateId() != null && command.confirmationToken() == null)
            return result(false, object, requestId, token, plan.tasks());
        if (command.confirmationToken() != null
                && !MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        command.confirmationToken().getBytes(StandardCharsets.UTF_8))) throw conflict();
        var before = ObjectStatusSnapshot.of(object);
        object.updateSpecifications(
                object.getSpecifications() == null
                        ? null
                        : object.getSpecifications().getTechnical(),
                values.commercial());
        object.changeStatus(rule, values);
        if (command.contractTypeId() != null
                && (object.getSpecifications() == null
                        || object.getSpecifications().getContractTypeReference() == null
                        || !command.contractTypeId()
                                .equals(object.getSpecifications()
                                        .getContractTypeReference()
                                        .getId()))) object.changeContractType(values.contractType());
        edit.accept(object);
        tasks.apply(context, plan, rule.getUnfinishedTaskAction().name(), initiatorId == null ? actor : initiatorId);
        writer.save(
                actor,
                object,
                before,
                context.generate() && plan.missingTemplate(),
                new ObjectStatusWriteMode(true, rule.getEffects().refusalEntry(), requestId));
        return result(true, object, requestId, null, plan.tasks());
    }

    static ObjectStatusResult result(
            boolean saved, RealEstateObject object, UUID request, String token, List<TaskTransitionPlan.Task> tasks) {
        return new ObjectStatusResult(
                saved, object.getId(), object.getStatus().getCode(), object.getVersion(), request, token, tasks);
    }

    static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Object or task preview changed; reload and confirm again");
    }
}
