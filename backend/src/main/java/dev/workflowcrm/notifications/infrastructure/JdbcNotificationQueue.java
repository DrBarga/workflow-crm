package dev.workflowcrm.notifications.infrastructure;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class JdbcNotificationQueue implements NotificationQueue {
    private final JdbcTemplate jdbc;
    private final ObjectAssignmentQuery users;

    JdbcNotificationQueue(JdbcTemplate jdbc, ObjectAssignmentQuery users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public void enqueue(UUID recipient, String key, String type, UUID object, UUID task) {
        jdbc.update(
                """
                insert into user_notifications(recipient_id,event_key,event_type,object_id,task_id)
                values (?,?,?,?,?) on conflict(recipient_id,event_key) do nothing
                """,
                recipient,
                key,
                type,
                object,
                task);
    }

    public void administrators(String key, String type, UUID object) {
        for (UUID user : users.activeMembers("SUPERADMIN")) enqueue(user, key, type, object, null);
    }
}
