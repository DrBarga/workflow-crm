package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JpaIcDecisionStore implements IcDecisionStore {
    private final EntityManager entities;
    private final ObjectHistoryRepository history;
    private final ObjectMapper json;

    JpaIcDecisionStore(EntityManager entities, ObjectHistoryRepository history, ObjectMapper json) {
        this.entities = entities;
        this.history = history;
        this.json = json;
    }

    public RealEstateObject lock(UUID id) {
        var object = entities.find(RealEstateObject.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (object == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        return object;
    }

    public ObjectStatusTransition transition(RealEstateObject object, InvestmentCommitteeDecision decision) {
        var rules = entities.createQuery(
                        "select t from ObjectStatusTransition t where t.fromStatus.id = :source"
                                + " and t.investmentCommitteeDecision = :decision and t.systemOnly = true and t.active = true",
                        ObjectStatusTransition.class)
                .setParameter("source", object.getStatus().getId())
                .setParameter("decision", decision)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getResultList();
        if (rules.size() != 1 || !rules.getFirst().getToStatus().isActive())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IC transition configuration is unavailable");
        return rules.getFirst();
    }

    public void save(RealEstateObject object) {
        entities.flush();
    }

    public void history(
            UUID actor, RealEstateObject object, String previousDecision, String decision, String previousStatus) {
        record Detail(String decision, java.time.LocalDate date, String conditions, String refusalComment) {}
        String detail;
        try {
            detail = json.writeValueAsString(new Detail(
                    decision,
                    object.getInvestmentCommitteeDate(),
                    object.getInvestmentCommitteeConditions(),
                    object.getInvestmentCommitteeRefusalComment()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot record IC decision", exception);
        }
        history.save(new ObjectHistory(
                object.getId(),
                actor,
                ObjectHistory.ActorType.USER,
                "IC_DECISION",
                previousDecision,
                detail,
                object.getVersion()));
        history.save(new ObjectHistory(
                object.getId(),
                null,
                ObjectHistory.ActorType.SYSTEM,
                "STATUS",
                previousStatus,
                object.getStatus().getCode(),
                object.getVersion()));
        entities.flush();
    }
}
