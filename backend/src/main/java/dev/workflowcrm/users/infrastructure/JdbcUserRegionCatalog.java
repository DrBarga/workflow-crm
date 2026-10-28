package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.UserRegionCatalog;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRegionCatalog implements UserRegionCatalog {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcUserRegionCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Option> regions(List<UUID> ids, boolean network) {
        if (!network && ids.isEmpty()) return List.of();
        return jdbc.query(
                "select id,code,name from regions " + (network ? "" : "where id in (:ids) ")
                        + "order by lower(name),id",
                network ? Map.of() : Map.of("ids", ids),
                (r, n) -> new Option(r.getObject("id", UUID.class), r.getString("code"), r.getString("name")));
    }
}
