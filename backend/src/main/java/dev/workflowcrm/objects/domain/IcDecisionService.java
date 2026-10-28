package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IcDecisionService {
    private final IcDecisionStore store;
    private final IcDecisionAccess access;
    private final ObjectTaskTransition tasks;
    private final IcDecisionFingerprint fingerprint;
    private final ObjectStatusWriter writer;

    IcDecisionService(
            IcDecisionStore store,
            IcDecisionAccess access,
            ObjectTaskTransition tasks,
            IcDecisionFingerprint fingerprint,
            ObjectStatusWriter writer) {
        this.store = store;
        this.access = access;
        this.tasks = tasks;
        this.fingerprint = fingerprint;
        this.writer = writer;
    }

    @Transactional
    public IcDecisionResult decide(UUID actor, UUID id, IcDecisionCommand command) {
        var object = store.lock(id);
        access.require(actor, object);
        if (!"IC_PREPARATION".equals(object.getStatus().getCode()))
            throw new UnprocessableInputException("status", "IC decision is available only during IC preparation");
        if (object.getVersion() != command.version()) throw conflict();
        var transition = store.transition(object, command.decision());
        var target = transition.getToStatus();
        String contractType = object.getSpecifications() == null
                ? null
                : object.getSpecifications().getContractType();
        var context = new ObjectTaskTransition.Context(
                id,
                object.getManagerId(),
                object.getRegionId(),
                object.getStatus().getId(),
                target.getId(),
                contractType,
                command.date(),
                command.decision() != InvestmentCommitteeDecision.REFUSED,
                object.getSpecifications() == null
                        ? null
                        : object.getSpecifications().getLegacyContractType(),
                transition.getUnfinishedTaskAction() == UnfinishedTaskAction.CANCEL_ALL,
                transition.getUnfinishedTaskAction() == UnfinishedTaskAction.CANCEL,
                command.assignees());
        var plan = tasks.preview(context);
        if (plan.tasks().stream().anyMatch(task -> task.assignment().selectionRequired())) {
            if (command.confirmationToken() != null)
                throw new UnprocessableInputException(
                        "assignees", "Choose an active assignee for every task before confirming");
            return result(false, object, command, null, plan.tasks());
        }
        String token = fingerprint.token(actor, object, transition, command, plan);
        if (plan.templateId() != null && command.confirmationToken() == null)
            return result(false, object, command, token, plan.tasks());
        if (command.confirmationToken() != null
                && !MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        command.confirmationToken().getBytes(StandardCharsets.UTF_8))) throw conflict();
        String previousStatus = object.getStatus().getCode();
        String previousDecision = object.getInvestmentCommitteeDecision() == null
                ? null
                : object.getInvestmentCommitteeDecision().name();
        var before = ObjectStatusSnapshot.of(object);
        object.recordIcDecision(command, transition);
        tasks.apply(context, plan, transition.getUnfinishedTaskAction().name(), actor);
        store.save(object);
        store.history(actor, object, previousDecision, command.decision().name(), previousStatus);
        writer.record(
                actor,
                object,
                before,
                context.generate() && plan.missingTemplate(),
                new ObjectStatusWriteMode(true, transition.getEffects().refusalEntry(), null));
        return result(true, object, command, null, plan.tasks());
    }

    private IcDecisionResult result(
            boolean saved,
            RealEstateObject object,
            IcDecisionCommand command,
            String token,
            List<TaskTransitionPlan.Task> tasks) {
        return new IcDecisionResult(
                saved,
                object.getId(),
                object.getStatus().getCode(),
                object.getVersion(),
                command.decision(),
                command.date(),
                command.conditions(),
                command.refusalComment(),
                token,
                tasks);
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Object or task preview changed; reload and confirm again");
    }
}
