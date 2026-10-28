package dev.workflowcrm.objects.domain;

import dev.workflowcrm.objects.api.ObjectRefusalTag;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectStatusService {
    private final ObjectStatusStore store;
    private final ObjectStatusAccess access;
    private final ObjectStatusExecutor executor;
    private final ObjectMilestoneFallback fallback;

    ObjectStatusService(
            ObjectStatusStore store,
            ObjectStatusAccess access,
            ObjectStatusExecutor executor,
            ObjectMilestoneFallback fallback) {
        this.store = store;
        this.access = access;
        this.executor = executor;
        this.fallback = fallback;
    }

    @Transactional
    public java.util.Map<String, Object> get(UUID actor, UUID id) {
        var object = store.lock(id);
        access.require(actor, object);
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("version", object.getVersion());
        result.put("status", object.getStatus().getCode());
        result.put("fields", ObjectStatusSnapshot.of(object));
        result.put("refusalTag", tag(object.getRefusalReason()));
        return result;
    }

    @Transactional
    public List<ObjectRefusalTag> refusalReasons(UUID actor, UUID id) {
        access.require(actor, store.lock(id));
        return store.manualRefusalReasons().stream()
                .map(ObjectStatusService::tag)
                .toList();
    }

    private static ObjectRefusalTag tag(RefusalReason reason) {
        return reason == null ? null : new ObjectRefusalTag(reason.getId(), reason.getCode(), reason.getName());
    }

    @Transactional
    public ObjectStatusResult change(UUID actor, UUID id, ObjectStatusCommand command, boolean milestone) {
        return execute(actor, id, command, milestone, false);
    }

    @Transactional
    public ObjectStatusResult preview(UUID actor, UUID id, ObjectStatusCommand command) {
        return execute(actor, id, command, false, true);
    }

    private ObjectStatusResult execute(
            UUID actor, UUID id, ObjectStatusCommand command, boolean milestone, boolean preview) {
        var object = store.lock(id);
        var member = access.require(actor, object);
        if (object.getVersion() != command.version()) throw ObjectStatusExecutor.conflict();
        if (milestone && command.contractSignedDate() == null && command.openingDate() == null)
            throw new UnprocessableInputException(
                    "openingPlannedDate", "Opening plan is calculated; edit construction acceptance or term");
        String field = milestone ? command.openingDate() != null ? "OPENING_DATE" : "CONTRACT_SIGNED_DATE" : null;
        var rules = store.transitions(object.getStatus().getId(), command.status(), field);
        if (rules.isEmpty() && command.status() != null) {
            var targetRules = store.transitions(null, command.status(), null);
            if (!targetRules.isEmpty()) access.requireEditable(member, targetRules);
        }
        var rule = access.select(member, object, rules);
        try {
            return executor.run(
                    actor,
                    object,
                    rule,
                    command,
                    false,
                    null,
                    null,
                    ignored -> {},
                    preview ? ObjectStatusExecutor.PreviewMode.REQUIRED : ObjectStatusExecutor.PreviewMode.NONE);
        } catch (ObjectTransitionConditionsException blocked) {
            if (!milestone) throw blocked;
            if (command.confirmationToken() != null) throw ObjectStatusExecutor.conflict();
            return fallback.save(actor, object, blocked);
        }
    }
}
