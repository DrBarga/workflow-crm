package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectCreationStore;
import dev.workflowcrm.objects.domain.ObjectDataSource;
import dev.workflowcrm.objects.domain.ObjectHistory;
import dev.workflowcrm.objects.domain.ObjectMilestones;
import dev.workflowcrm.objects.domain.ObjectRegistration;
import dev.workflowcrm.objects.domain.ObjectStatus;
import dev.workflowcrm.objects.domain.ObjectType;
import dev.workflowcrm.objects.domain.RealEstateObject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JpaObjectCreationStore implements ObjectCreationStore {
    private final jakarta.persistence.EntityManager entities;
    private final JdbcTemplate jdbc;
    private final RealEstateObjectRepository objects;
    private final ObjectTypeRepository types;
    private final ObjectStatusRepository statuses;
    private final ObjectHistoryRepository history;

    JpaObjectCreationStore(
            jakarta.persistence.EntityManager entities,
            JdbcTemplate jdbc,
            RealEstateObjectRepository objects,
            ObjectTypeRepository types,
            ObjectStatusRepository statuses,
            ObjectHistoryRepository history) {
        this.entities = entities;
        this.jdbc = jdbc;
        this.objects = objects;
        this.types = types;
        this.statuses = statuses;
        this.history = history;
    }

    public void lockRequest(UUID actor, String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", row -> {}, actor + ":" + key);
    }

    public void lockCreation() {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended('objects:duplicate-check',0))", row -> {});
    }

    public void recordDuplicateConfirmation(UUID actor, RealEstateObject object, long matchCount) {
        history.save(new ObjectHistory(
                object.getId(),
                actor,
                ObjectHistory.ActorType.USER,
                "DUPLICATES_CONFIRMED",
                null,
                Long.toString(matchCount),
                object.getVersion()));
    }

    public Optional<PreviousRequest> previousRequest(UUID actor, String key) {
        return jdbc
                .query(
                        "select request_hash, object_id, contact_id from object_creation_requests where actor_id = ? and request_key = ?",
                        (rs, n) -> new PreviousRequest(
                                rs.getString(1),
                                objects.findById(rs.getObject(2, UUID.class))
                                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND, "Object was deleted")),
                                rs.getObject(3, UUID.class)),
                        actor,
                        key)
                .stream()
                .findFirst();
    }

    public Optional<ObjectType> type(String code) {
        return types.findByCode(code).filter(ObjectType::isActive);
    }

    public Optional<ObjectDataSource> dataSource(UUID id) {
        return Optional.ofNullable(entities.find(ObjectDataSource.class, id)).filter(ObjectDataSource::isActive);
    }

    public RealEstateObject save(ObjectRegistration registration) {
        return save(registration, null);
    }

    public RealEstateObject save(ObjectRegistration registration, ObjectMilestones dates) {
        var found = statuses.findByCode("FOUND").filter(ObjectStatus::isActive).orElseThrow();
        var created = new RealEstateObject(registration, found);
        if (dates != null) created.schedule(dates);
        var object = objects.saveAndFlush(created);
        entities.refresh(object);
        return object;
    }

    public void recordCreation(UUID actor, String key, String hash, RealEstateObject object, UUID contactId) {
        jdbc.update(
                "insert into object_creation_requests(actor_id, request_key, request_hash, object_id, contact_id) values (?, ?, ?, ?, ?)",
                actor,
                key,
                hash,
                object.getId(),
                contactId);
        history.save(new ObjectHistory(
                object.getId(), actor, ObjectHistory.ActorType.USER, "CREATED", null, "FOUND", object.getVersion()));
    }
}
