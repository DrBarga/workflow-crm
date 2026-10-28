package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ObjectListFullReader {
    private final RealEstateObjectRepository objects;
    private final NamedParameterJdbcTemplate jdbc;

    ObjectListFullReader(RealEstateObjectRepository objects, NamedParameterJdbcTemplate jdbc) {
        this.objects = objects;
        this.jdbc = jdbc;
    }

    Map<UUID, ObjectListItem.Full> read(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, UUID> contacts = new HashMap<>();
        jdbc.query(
                "select link.object_id, min(link.contact_id::text)::uuid as contact_id from object_contacts link"
                        + " join contacts c on c.id=link.contact_id where link.object_id in (:ids)"
                        + " and c.deleted_at is null group by link.object_id",
                Map.of("ids", ids),
                rs -> {
                    contacts.put(rs.getObject("object_id", UUID.class), rs.getObject("contact_id", UUID.class));
                });
        return objects.findForList(ids).stream()
                .collect(Collectors.toMap(RealEstateObject::getId, o -> view(o, contacts.get(o.getId()))));
    }

    private ObjectListItem.Full view(RealEstateObject o, UUID contactId) {
        var spec = o.getSpecifications();
        return new ObjectListItem.Full(
                CreatedObjectView.forList(o, contactId),
                ObjectListWorkflow.of(o),
                spec == null ? null : spec.getTechnicalSpecifications(),
                spec == null ? null : spec.getCommercialTerms());
    }
}
