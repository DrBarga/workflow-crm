package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.StatusTaskTriggerQuery;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcStatusTaskTriggerQuery implements StatusTaskTriggerQuery {
    private final JdbcTemplate jdbc;

    JdbcStatusTaskTriggerQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean isTrigger(UUID statusId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select exists(select 1 from task_templates where trigger_status_id=? and is_active)
                """,
                Boolean.class,
                statusId));
    }
}
