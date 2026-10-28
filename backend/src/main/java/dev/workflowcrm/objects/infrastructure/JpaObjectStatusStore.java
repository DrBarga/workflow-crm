package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JpaObjectStatusStore implements ObjectStatusStore {
    private final EntityManager entities;
    private final ObjectHistoryRepository history;

    JpaObjectStatusStore(EntityManager entities, ObjectHistoryRepository history) {
        this.entities = entities;
        this.history = history;
    }

    public RealEstateObject lock(UUID id) {
        var object = entities.find(RealEstateObject.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (object == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        return object;
    }

    public ObjectStatus status(String code) {
        return entities.createQuery(
                        "select s from ObjectStatus s where s.code=:code and s.active=true", ObjectStatus.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new UnprocessableInputException("status", "Choose an active object status"));
    }

    public List<ObjectStatusTransition> transitions(UUID source, String target, String field) {
        return entities.createQuery(
                        """
                select t from ObjectStatusTransition t where (:source is null or t.fromStatus.id=:source)
                and t.active=true and t.toStatus.active=true
                and ((:target is not null and t.toStatus.code=:target)
                     or (:field is not null and t.effects.milestoneField=:field)
                     or (:target is null and :field is null))
                order by t.code
                """,
                        ObjectStatusTransition.class)
                .setParameter("source", source)
                .setParameter("target", target)
                .setParameter("field", field)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getResultList();
    }

    public RefusalReason refusalReason(UUID id) {
        var value = entities.find(RefusalReason.class, id, LockModeType.PESSIMISTIC_READ);
        if (value == null || !value.isActive() || value.isSystemOnly())
            throw new UnprocessableInputException("refusalReasonId", "Choose an active manual refusal reason");
        return value;
    }

    public ContractType contractType(UUID id) {
        var value = entities.find(ContractType.class, id, LockModeType.PESSIMISTIC_READ);
        if (value == null || !value.isActive())
            throw new UnprocessableInputException("contractTypeId", "Choose an active contract type");
        return value;
    }

    public List<RefusalReason> manualRefusalReasons() {
        return entities.createQuery(
                        "select r from RefusalReason r where r.active=true and r.systemOnly=false order by r.name, r.code",
                        RefusalReason.class)
                .getResultList();
    }

    public void save(RealEstateObject object) {
        entities.flush();
    }

    public void history(
            UUID actor, RealEstateObject object, String type, Map<String, String> before, Map<String, String> after) {
        for (String field : before.keySet()) {
            if (!java.util.Objects.equals(before.get(field), after.get(field)))
                history.save(new ObjectHistory(
                        object.getId(),
                        actor,
                        ObjectHistory.ActorType.USER,
                        field,
                        before.get(field),
                        after.get(field),
                        object.getVersion()));
        }
        if (!"STATUS".equals(type))
            history.save(new ObjectHistory(
                    object.getId(),
                    actor,
                    ObjectHistory.ActorType.USER,
                    type,
                    null,
                    "No active task template",
                    object.getVersion()));
        entities.flush();
    }
}
