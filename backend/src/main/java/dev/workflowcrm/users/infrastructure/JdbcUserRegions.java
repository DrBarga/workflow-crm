package dev.workflowcrm.users.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRegions {
    private final JdbcTemplate jdbc;

    JdbcUserRegions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<UUID> assigned(UUID user) {
        return jdbc.queryForList("select region_id from user_regions where user_id=?", UUID.class, user).stream()
                .sorted()
                .toList();
    }
}
