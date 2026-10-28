package dev.workflowcrm.users.infrastructure;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev & !stage & !prod")
public class LocalTestUserSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalTestUserSeed.class);
    private final JdbcTemplate database;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    public LocalTestUserSeed(
            JdbcTemplate database,
            PasswordEncoder passwords,
            @Value("${app.dev.test-user.email:}") String email,
            @Value("${app.dev.test-user.password:}") String password) {
        this.database = database;
        this.passwords = passwords;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank()) {
            log.info("Local test user seed skipped: SEED_TEST_USER_EMAIL is empty");
            return;
        }
        if (Boolean.TRUE.equals(database.queryForObject(
                "select exists(select 1 from users where lower(email) = ?)", Boolean.class, email))) {
            log.info("Local test user seed skipped: account already exists");
            return;
        }
        if (!validEmail()) {
            log.warn("Local test user seed skipped: SEED_TEST_USER_EMAIL is invalid");
            return;
        }
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            log.warn(
                    "Local test user seed skipped: SEED_TEST_USER_PASSWORD requires at least 12 characters and at most 72 UTF-8 bytes");
            return;
        }
        database.update("insert into regions(code, name) values ('DEMO_MAIL', 'Local email testing') "
                + "on conflict (code) do nothing");
        int created = database.update(
                "insert into users(email, password_hash, full_name, role_id, status, password_setup_required) "
                        + "select ?, ?, 'Local email tester', roles.id, 'ACTIVE', false "
                        + "from roles cross join regions where roles.code = 'MANAGER' and regions.code = 'DEMO_MAIL' "
                        + "on conflict do nothing",
                email,
                passwords.encode(password));
        if (created == 1)
            database.update(
                    "insert into user_regions(user_id,region_id) select u.id,r.id from users u cross join regions r where lower(u.email)=? and r.code='DEMO_MAIL'",
                    email);
        log.info("Local test user seed completed: {} account created", created);
    }

    private boolean validEmail() {
        if (email.length() > 320) return false;
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return email.equals(address.getAddress()) && email.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}
