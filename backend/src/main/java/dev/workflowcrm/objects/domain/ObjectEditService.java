package dev.workflowcrm.objects.domain;

import dev.workflowcrm.contacts.api.ObjectContactSelection;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectEditService {
    private final ObjectEditAccess access;
    private final ObjectStatusStore status;
    private final ObjectEditStore edits;
    private final ObjectEditPreparation preparation;
    private final ObjectEditTransition transitions;
    private final ObjectEditWriter writer;
    private final ObjectContactSelection contacts;

    ObjectEditService(
            ObjectEditAccess access,
            ObjectStatusStore status,
            ObjectEditStore edits,
            ObjectEditPreparation preparation,
            ObjectEditTransition transitions,
            ObjectEditWriter writer,
            ObjectContactSelection contacts) {
        this.access = access;
        this.status = status;
        this.edits = edits;
        this.preparation = preparation;
        this.transitions = transitions;
        this.writer = writer;
        this.contacts = contacts;
    }

    public void requireEditor(UUID actor, Set<String> fields) {
        access.requireEditor(actor);
        access.requireContractFields(actor, fields);
    }

    @Transactional
    public ObjectStatusResult patch(UUID actor, UUID id, ObjectPatchCommand command) {
        access.requireEditor(actor);
        var object = status.lock(id);
        var member = access.require(actor, object);
        access.patch(member, object, command.fields());
        if (command.version() != object.getVersion()) throw new ObjectEditConflictException(object.getVersion());
        var prepared = preparation.prepare(object, command);
        var before = edits.snapshot(object);
        Runnable edit = () -> {
            prepared.apply(object);
            if (command.fields().contains("contactId")
                    && !command.values().contactId().equals(edits.contactId(id))) {
                var contact = contacts.replace(actor, id, command.values().contactId());
                object.selectContact(
                        new ObjectContact(contact.name(), contact.role(), contact.phone(), contact.email()));
            }
        };
        ObjectStatusResult transition;
        try {
            transition = transitions.apply(actor, object, command, prepared, edit);
        } catch (ObjectTransitionConditionsException blocked) {
            if (command.confirmationToken() != null) throw new ObjectEditConflictException(object.getVersion());
            edit.run();
            boolean saved = writer.save(actor, object, before, "FIELDS_CHANGED", null);
            return ObjectStatusExecutor.result(saved, object, null, null, List.of())
                    .withConditions(blocked.conditions());
        }
        if (transition != null) {
            if (transition.saved()) writer.record(actor, object, before, "FIELDS_CHANGED", null);
            return transition;
        }
        edit.run();
        boolean saved = writer.save(actor, object, before, "FIELDS_CHANGED", null);
        return ObjectStatusExecutor.result(saved, object, null, null, List.of());
    }
}
