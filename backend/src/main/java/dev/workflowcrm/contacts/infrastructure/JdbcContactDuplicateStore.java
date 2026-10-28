package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.ContactDuplicate;
import dev.workflowcrm.contacts.domain.ContactDuplicateStore;
import dev.workflowcrm.contacts.domain.ContactScope;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContactDuplicateStore implements ContactDuplicateStore {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcContactDuplicateStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContactDuplicate> find(List<String> phoneKeys, UUID excludedId, ContactScope scope) {
        if (phoneKeys.isEmpty()) return List.of();
        var params = new HashMap<String, Object>();
        params.put("keys", phoneKeys);
        params.put("excluded", excludedId);
        params.put("actor", scope.actorId());
        params.put("network", scope.network());
        params.put("writer", scope.writer());
        params.put("manager", scope.role().equals("MANAGER"));
        params.put("regional", scope.role().equals("REGIONAL_MANAGER"));
        params.put("contract", scope.role().equals("CONTRACT_MANAGER"));
        params.put("hasRegions", !scope.regions().isEmpty());
        params.put("regions", scope.regions().isEmpty() ? List.of(new UUID(0, 0)) : scope.regions());
        return jdbc.query(
                """
                select c.id,c.name,c.role from contacts c
                where c.deleted_at is null and c.normalized_phone in (:keys) and c.id<>:excluded
                and (:network or (:writer and c.author_id=:actor
                    and not exists(select 1 from object_contacts own_link where own_link.contact_id=c.id))
                    or exists(select 1 from object_contacts link
                        join objects o on o.id=link.object_id join object_statuses s on s.id=o.status_id
                        where link.contact_id=c.id and (
                            (:manager and o.manager_id=:actor and :hasRegions and o.region_id in (:regions))
                            or (:regional and :hasRegions and o.region_id in (:regions))
                            or (:contract and s.code in ('CONTRACT_APPROVAL','CONTRACT_SIGNED')))))
                order by c.created_at,c.id limit 20
                """,
                params,
                (r, n) ->
                        new ContactDuplicate(r.getObject("id", UUID.class), r.getString("name"), r.getString("role")));
    }
}
