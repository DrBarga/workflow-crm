package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.TelegramBinding;
import java.security.SecureRandom;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class LoginSessionFlow {
    private final AuthenticationService authentication;
    private final AuthenticationUserQuery users;
    private final TelegramBinding binding;
    private final DeviceTrust devices;
    private final ChallengeStore challenges;
    private final EmailChallengeSender email;
    private final TelegramChallengeSender telegram;
    private final DevicePolicy devicePolicy;
    private final SecureRandom random = new SecureRandom();

    LoginSessionFlow(
            AuthenticationService authentication,
            AuthenticationUserQuery users,
            TelegramBinding binding,
            DeviceTrust devices,
            ChallengeStore challenges,
            EmailChallengeSender email,
            TelegramChallengeSender telegram,
            DevicePolicy devicePolicy) {
        this.authentication = authentication;
        this.users = users;
        this.binding = binding;
        this.devices = devices;
        this.challenges = challenges;
        this.email = email;
        this.telegram = telegram;
        this.devicePolicy = devicePolicy;
    }

    public LoginStart start(LoginCredentials credentials, SessionMetadata metadata) {
        VerifiedLogin login = authentication.verify(credentials, metadata);
        UUID userId = login.user().id();
        if (devices.blocked(userId, metadata.deviceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is blocked");
        }
        if (devices.trusted(userId, metadata.deviceId())
                && (devicePolicy.confirmationMode() != ConfirmationMode.EVERY_LOGIN
                        || devices.consumeFirstLogin(userId, metadata.deviceId()))) {
            return LoginStart.completed(authentication.issue(login));
        }
        challenges.reserve(userId);
        OptionalLong chatId = binding.chatId(userId);
        if (chatId.isEmpty() || !telegram.available()) {
            return LoginStart.pending(startEmail(login));
        }
        LoginChallenge challenge = challenges.create(login, ChallengeStatus.PENDING_TELEGRAM, null);
        try {
            telegram.send(chatId.orElseThrow(), challenge);
            return LoginStart.pending(challenge);
        } catch (RuntimeException exception) {
            challenges.transition(challenge.id(), ChallengeStatus.PENDING_TELEGRAM, ChallengeStatus.REJECTED);
            return LoginStart.pending(startEmail(login));
        }
    }

    public LoginResult complete(String id, String deviceId) {
        LoginChallenge challenge = challenges
                .consume(id, deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Challenge is not approved"));
        if (devices.blocked(challenge.userId(), deviceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is blocked");
        }
        var user = users.findById(challenge.userId())
                .filter(candidate -> candidate.active())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled"));
        devices.trust(user.id(), deviceId);
        return authentication.issue(new VerifiedLogin(
                user, new SessionMetadata(deviceId, challenge.userAgent(), challenge.ip()), challenge.email()));
    }

    private LoginChallenge startEmail(VerifiedLogin login) {
        if (!email.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email confirmation is unavailable");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        LoginChallenge challenge = challenges.create(login, ChallengeStatus.PENDING_EMAIL, code);
        try {
            email.send(login.email(), code);
        } catch (RuntimeException exception) {
            challenges.transition(challenge.id(), ChallengeStatus.PENDING_EMAIL, ChallengeStatus.REJECTED);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email confirmation is unavailable");
        }
        return challenge;
    }
}
