package dev.workflowcrm.users.domain;

import dev.workflowcrm.shared.security.PasswordPolicy;
import dev.workflowcrm.users.api.UserOnboardingSecurity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserInvitationService {
    private static final Logger log = LoggerFactory.getLogger(UserInvitationService.class);

    private final UserManagementStore store;
    private final UserInvitationSender sender;
    private final UserProvisioningProperties properties;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final UserOnboardingSecurity onboarding;
    private final SecureRandom random = new SecureRandom();

    public UserInvitationService(
            UserManagementStore store,
            UserInvitationSender sender,
            UserProvisioningProperties properties,
            PasswordEncoder passwords,
            Clock clock,
            UserOnboardingSecurity onboarding) {
        this.store = store;
        this.sender = sender;
        this.properties = properties;
        this.passwords = passwords;
        this.clock = clock;
        this.onboarding = onboarding;
    }

    public void issue(User user) {
        deliver(user, true);
    }

    public void resend(User user) {
        deliver(user, false);
    }

    public UUID open(String token) {
        Instant now = clock.instant();
        UserInvitation invitation = store.lockInvitation(hash(token))
                .filter(value -> value.usableAt(now))
                .orElseThrow(UserInvitationService::invalidInvitation);
        User user = store.find(invitation.userId()).orElseThrow(UserInvitationService::invalidInvitation);
        if (user.getStatus() != UserStatus.DISABLED || !user.isPasswordSetupRequired()) {
            throw invalidInvitation();
        }
        return user.getId();
    }

    private void deliver(User user, boolean tolerateFailure) {
        Instant now = clock.instant();
        String token = newToken();
        store.invalidateInvitations(user.getId(), now);
        store.saveInvitation(UUID.randomUUID(), user.getId(), hash(token), now.plus(properties.invitationTtl()));
        try {
            sender.sendPasswordSetup(user.getEmail(), user.getFullName(), token);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to send password setup invitation for user {} ({})",
                    user.getId(),
                    exception.getClass().getSimpleName());
            if (!tolerateFailure) {
                throw exception;
            }
        }
    }

    public String accept(String token, String password, String presentedCookie) {
        Instant now = clock.instant();
        UserInvitation invitation = store.lockInvitation(hash(token))
                .filter(value -> value.usableAt(now))
                .orElseThrow(UserInvitationService::invalidInvitation);
        User user = store.lock(invitation.userId()).orElseThrow(UserInvitationService::invalidInvitation);
        if (user.getStatus() != UserStatus.DISABLED || !user.isPasswordSetupRequired()) {
            throw invalidInvitation();
        }
        PasswordPolicy.validate(password, user.getEmail(), user.getFullName());
        UserAccessState before = UserAccessState.of(user);
        user.activateWithPassword(passwords.encode(password));
        store.save(user);
        store.recordChange(user.getId(), user.getId(), before, UserAccessState.of(user));
        store.consumeInvitation(invitation.id(), now);
        return onboarding.trustInvitationDevice(user.getId(), presentedCookie);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw invalidInvitation();
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.GONE, "Invitation is invalid or expired");
    }
}
