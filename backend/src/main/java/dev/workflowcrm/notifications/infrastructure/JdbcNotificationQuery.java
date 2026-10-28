package dev.workflowcrm.notifications.infrastructure;

import dev.workflowcrm.notifications.api.NotificationQuery;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JdbcNotificationQuery implements NotificationQuery {
    private final JdbcTemplate jdbc;
    private final ObjectAssignmentQuery users;

    JdbcNotificationQuery(JdbcTemplate jdbc, ObjectAssignmentQuery users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public List<Notification> latest(UUID recipient) {
        if (users.member(recipient).filter(ObjectAssignmentQuery.Member::active).isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return jdbc.query(
                """
                select * from user_notifications where recipient_id=? order by created_at desc,id desc limit 100
                """,
                (r, n) -> new Notification(
                        r.getObject("id", UUID.class),
                        r.getString("event_type"),
                        r.getObject("object_id", UUID.class),
                        r.getObject("task_id", UUID.class),
                        r.getTimestamp("created_at").toInstant()),
                recipient);
    }
}
