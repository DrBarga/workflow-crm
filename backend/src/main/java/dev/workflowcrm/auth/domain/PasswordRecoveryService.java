package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.PasswordResetAccount;
import dev.workflowcrm.users.api.PasswordResetAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PasswordRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final PasswordResetAccountService accounts;
    private final PasswordResetTokenStore tokens;
    private final PasswordResetSender sender;
    private final PasswordRecoveryProperties properties;
    private final RefreshSessionStore sessions;
    private final LoginAttemptTracker loginAttempts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(
            PasswordResetAccountService accounts,
            PasswordResetTokenStore tokens,
            PasswordResetSender sender,
            PasswordRecoveryProperties properties,
            RefreshSessionStore sessions,
            LoginAttemptTracker loginAttempts,
            Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.sender = sender;
        this.properties = properties;
        this.sessions = sessions;
        this.loginAttempts = loginAttempts;
        this.clock = clock;
    }

    public PasswordResetRequestResult request(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        String fingerprint = hash(email).substring(0, 16);
        LOG.info("Password recovery requested for email fingerprint {}", fingerprint);
        if (!sender.available()) {
            LOG.warn("Password recovery email is unavailable for request {}", fingerprint);
            return PasswordResetRequestResult.DELIVERY_UNAVAILABLE;
        }

        var account = accounts.lockEligibleByEmail(email);
        if (account.isEmpty()) {
            return PasswordResetRequestResult.ACCEPTED;
        }

        PasswordResetAccount user = account.orElseThrow();
        Instant now = clock.instant();
        String token = newToken();
        tokens.invalidateForUser(user.id(), now);
        tokens.save(UUID.randomUUID(), user.id(), hash(token), now.plus(properties.tokenTtl()));
        try {
            sender.send(user.email(), user.fullName(), token);
            LOG.info("Password recovery email accepted for user {}", user.id());
            return PasswordResetRequestResult.ACCEPTED;
        } catch (PasswordResetDeliveryException exception) {
            tokens.invalidateForUser(user.id(), clock.instant());
            LOG.warn(
                    "Password recovery email delivery failed for user {} ({})",
                    user.id(),
                    exception.getCause() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getCause().getClass().getSimpleName());
            return PasswordResetRequestResult.DELIVERY_UNAVAILABLE;
        }
    }

    public void reset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokens.lock(hashRequired(rawToken))
                .filter(candidate -> candidate.usableAt(now))
                .orElseThrow(PasswordRecoveryService::invalidToken);
        PasswordResetAccount account =
                accounts.changePassword(token.userId(), newPassword).orElseThrow(PasswordRecoveryService::invalidToken);

        sessions.revokeUser(account.id());
        loginAttempts.clear(account.email());
        tokens.consume(token.id(), now);
        LOG.info("Password reset completed and sessions revoked for user {}", account.id());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashRequired(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        return hash(token);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.GONE, "Password reset link is invalid or expired");
    }
}
