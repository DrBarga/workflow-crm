package dev.workflowcrm;

import dev.workflowcrm.auth.domain.BindingTokens;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ProfileTelegramFixtures {
    private final JdbcTemplate jdbc;
    private final BindingTokens tokens;

    ProfileTelegramFixtures(JdbcTemplate jdbc, BindingTokens tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    void create(UUID... users) {
        for (UUID id : users)
            jdbc.update(
                    """
                insert into users(id,email,password_hash,full_name,role_id,status)
                select ?,?,'test-hash','Profile Test User',id,'ACTIVE' from roles where code='SUPERADMIN'
                """,
                    id,
                    id + "@example.com");
    }

    void remove(UUID... users) {
        for (UUID id : users) {
            tokens.invalidate(id);
            jdbc.update("delete from telegram_binding_events where user_id=?", id);
            jdbc.update("delete from users where id=?", id);
        }
    }
}
