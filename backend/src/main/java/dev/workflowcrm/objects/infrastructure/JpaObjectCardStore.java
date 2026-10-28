package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JpaObjectCardStore implements ObjectCardStore {
    private final JdbcTemplate jdbc;
    private final RealEstateObjectRepository objects;
    private final ObjectStatusTransitionRepository transitions;

    JpaObjectCardStore(
            JdbcTemplate jdbc, RealEstateObjectRepository objects, ObjectStatusTransitionRepository transitions) {
        this.jdbc = jdbc;
        this.objects = objects;
        this.transitions = transitions;
    }

    public Summary summary(UUID id) {
        return jdbc
                .query(
                        "select id,name,entrance_longitude,entrance_latitude,manager_id,region_id from objects where id=?",
                        (r, n) -> new Summary(
                                r.getObject("id", UUID.class),
                                r.getString("name"),
                                new CreatedObjectView.Coordinates(
                                        r.getBigDecimal("entrance_longitude"), r.getBigDecimal("entrance_latitude")),
                                r.getObject("manager_id", UUID.class),
                                r.getObject("region_id", UUID.class)),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(JpaObjectCardStore::missing);
    }

    public RealEstateObject full(UUID id) {
        return objects.findForList(List.of(id)).stream().findFirst().orElseThrow(JpaObjectCardStore::missing);
    }

    public UUID contactId(UUID id) {
        return jdbc.queryForObject(
                "select min(link.contact_id::text)::uuid from object_contacts link join contacts c on c.id=link.contact_id"
                        + " where link.object_id=? and c.deleted_at is null",
                UUID.class,
                id);
    }

    public List<ObjectStatusTransition> transitions(UUID statusId) {
        return transitions.findByFromStatusIdAndActiveTrue(statusId).stream()
                .filter(r -> r.getToStatus().isActive())
                .sorted(java.util.Comparator.comparing(ObjectStatusTransition::getCode))
                .toList();
    }

    public int preparationOrder() {
        return jdbc.queryForObject("select sort_order from object_statuses where code='IC_PREPARATION'", Integer.class);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
    }
}
