package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.*;
import dev.workflowcrm.shared.read.ReadPage;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContactSearchStore implements ContactSearchStore {
    private static final String WHERE =
            """
            from contacts c where c.deleted_at is null
            and (cast(:object as uuid) is null or exists(select 1 from object_contacts selected where selected.contact_id=c.id and selected.object_id=:object))
            and (:empty or lower(c.name) like :name escape '!' or (:phoneSearch and c.normalized_phone like :phone))
            and (:network or (:writer and c.author_id=:actor and not exists(select 1 from object_contacts own_link where own_link.contact_id=c.id))
                or exists(select 1 from object_contacts link join objects o on o.id=link.object_id
                    join object_statuses s on s.id=o.status_id
                    where link.contact_id=c.id and ((:manager and o.manager_id=:actor and :hasRegions and o.region_id in (:regions))
                        or (:regional and :hasRegions and o.region_id in (:regions))
                        or (:contract and s.code in ('CONTRACT_APPROVAL','CONTRACT_SIGNED')))))
            """;
    private final NamedParameterJdbcTemplate jdbc;

    JdbcContactSearchStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReadPage.Result<ContactSummary> search(ContactScope scope, UUID object, String query, ReadPage page) {
        var p = new HashMap<String, Object>();
        p.put("object", object);
        p.put("empty", query.isEmpty());
        p.put(
                "name",
                "%"
                        + query.toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_") + "%");
        String digits = query.replaceAll("[^0-9]", "");
        p.put("phoneSearch", digits.length() >= 3 && query.matches("[+0-9\\s().-]+"));
        p.put("phone", "%" + digits + "%");
        p.put("actor", scope.actorId());
        p.put("network", scope.network());
        p.put("writer", scope.writer());
        p.put("manager", scope.role().equals("MANAGER"));
        p.put("regional", scope.role().equals("REGIONAL_MANAGER"));
        p.put("contract", scope.role().equals("CONTRACT_MANAGER"));
        p.put("hasRegions", !scope.regions().isEmpty());
        p.put("regions", scope.regions().isEmpty() ? List.of(new UUID(0, 0)) : scope.regions());
        p.put("limit", page.size());
        p.put("offset", page.offset());
        long total = jdbc.queryForObject("select count(*) " + WHERE, p, Long.class);
        var rows = jdbc.query(
                "select c.id,c.name,c.role,c.phone,c.email,c.contact_type_id,c.version " + WHERE
                        + " order by lower(c.name),c.id limit :limit offset :offset",
                p,
                (r, n) -> new ContactSummary(
                        r.getObject("id", UUID.class),
                        r.getString("name"),
                        r.getString("role"),
                        r.getString("phone") == null ? null : "***",
                        r.getString("email"),
                        r.getObject("contact_type_id", UUID.class),
                        r.getLong("version")));
        return page.result(rows, total);
    }
}
