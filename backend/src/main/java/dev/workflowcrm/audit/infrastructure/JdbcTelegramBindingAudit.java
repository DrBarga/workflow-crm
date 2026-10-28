package dev.workflowcrm.audit.infrastructure;

import dev.workflowcrm.audit.api.TelegramBindingAudit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTelegramBindingAudit implements TelegramBindingAudit {
    private final JdbcTemplate jdbc;

    JdbcTelegramBindingAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Optional<UUID> latestEvent(UUID userId) {
        return jdbc
                .query(
                        "select id from telegram_binding_events where user_id=? order by created_at desc, id desc limit 1",
                        (row, index) -> row.getObject("id", UUID.class),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean hasPreviousBinding(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from telegram_binding_events where user_id = ? and action in ('BOUND', 'REBOUND'))",
                Boolean.class,
                userId));
    }

    @Override
    public void record(UUID userId, long chatId, Action action) {
        jdbc.update(
                "insert into telegram_binding_events (id, user_id, chat_id, action, created_at) "
                        + "values (?, ?, ?, ?, clock_timestamp())",
                UUID.randomUUID(),
                userId,
                chatId,
                action.name());
    }
}
