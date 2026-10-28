package dev.workflowcrm.objects.domain;

import dev.workflowcrm.audit.api.ObjectDuplicateAudit;
import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.contacts.api.ContactRegistration;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectCreationService {
    private final ObjectCreationStore store;
    private final ObjectCreationAccess access;
    private final ObjectCreationPreparation preparation;
    private final ObjectDuplicateSearch duplicates;
    private final ObjectDuplicateAudit audit;
    private final ContactRegistration contacts;

    public ObjectCreationService(
            ObjectCreationStore store,
            ObjectCreationAccess access,
            ObjectCreationPreparation preparation,
            ObjectDuplicateSearch duplicates,
            ObjectDuplicateAudit audit,
            ContactRegistration contacts) {
        this.store = store;
        this.access = access;
        this.preparation = preparation;
        this.duplicates = duplicates;
        this.audit = audit;
        this.contacts = contacts;
    }

    @Transactional
    public CreatedObjectView create(
            UUID actorId, String key, String hash, CreateObjectCommand request, boolean confirmDuplicates) {
        if (request.contactId() != null && request.contact() != null)
            throw new UnprocessableInputException("contactId", "Choose contactId or a new contact, not both");
        var actor = access.creator(actorId);
        store.lockRequest(actorId, key);
        var previous = store.previousRequest(actorId, key);
        if (previous.isPresent()) {
            if (!previous.get().hash().equals(hash))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Idempotency-Key was already used for a different request");
            if ("MANAGER".equals(actor.role())
                    && !actor.id().equals(previous.get().object().getManagerId()))
                throw new org.springframework.security.access.AccessDeniedException("Access denied");
            access.manager(
                    actor,
                    "MANAGER".equals(actor.role())
                            ? null
                            : previous.get().object().getManagerId(),
                    previous.get().object().getRegionId());
            return CreatedObjectView.of(previous.get().object(), previous.get().contactId());
        }
        if (audit.cancellationHash(actorId, key).isPresent()) throw conflict();
        var registration = preparation.prepare(actor, request);
        store.lockCreation();
        var matches = duplicates.find(registration);
        if (matches.total() > 0 && !confirmDuplicates)
            throw new ObjectDuplicatesFoundException(ObjectDuplicateReport.visible(matches, actor));
        var object = store.save(registration);
        var contact = request.contact();
        UUID contactId = contact == null && request.contactId() == null
                ? null
                : contacts.attach(
                        object.getId(),
                        actorId,
                        request.contactId(),
                        contact == null
                                ? null
                                : new ContactDetails(
                                        contact.getName(), contact.getRole(), contact.getPhone(), contact.getEmail()));
        store.recordCreation(actorId, key, hash, object, contactId);
        if (matches.total() > 0) {
            audit.record(actorId, key, hash, object.getId(), matches.total(), matches.ids());
            store.recordDuplicateConfirmation(actorId, object, matches.total());
        }
        return CreatedObjectView.of(object, contactId);
    }

    @Transactional
    public void cancel(UUID actorId, String key, String hash, CreateObjectCommand request) {
        var actor = access.creator(actorId);
        store.lockRequest(actorId, key);
        var previous = audit.cancellationHash(actorId, key);
        if (previous.isPresent()) {
            if (!previous.get().equals(hash)) throw conflict();
            preparation.authorize(actor, request);
            return;
        }
        if (store.previousRequest(actorId, key).isPresent()) throw conflict();
        var registration = preparation.prepare(actor, request);
        store.lockCreation();
        var matches = duplicates.find(registration);
        audit.record(actorId, key, hash, null, matches.total(), matches.ids());
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already completed; use a new key");
    }
}
