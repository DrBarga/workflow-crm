package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectCatalogStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectCatalogStore implements ObjectCatalogStore {
    private final JdbcTemplate jdbc;

    JdbcObjectCatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Option> dataSources() {
        return options("object_data_sources");
    }

    public List<Option> contractTypes() {
        return options("contract_types");
    }

    private List<Option> options(String table) {
        return jdbc.query(
                "select id,code,name from " + table + " where is_active order by lower(name),id",
                (r, n) -> new Option(r.getObject("id", UUID.class), r.getString("code"), r.getString("name")));
    }
}
