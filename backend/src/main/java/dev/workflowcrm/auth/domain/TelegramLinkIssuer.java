package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.TelegramBinding;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TelegramLinkIssuer {
    public static final Duration PROFILE_TTL = Duration.ofMinutes(10);
    private final AuthenticationUserQuery users;
    private final TelegramBinding binding;
    private final BindingTokens tokens;
    private final ChallengeStore challenges;
    private final RefreshSessionStore sessions;
    private final TelegramSettings settings;

    public TelegramLinkIssuer(
            AuthenticationUserQuery users,
            TelegramBinding binding,
            BindingTokens tokens,
            ChallengeStore challenges,
            RefreshSessionStore sessions,
            TelegramSettings settings) {
        this.users = users;
        this.binding = binding;
        this.tokens = tokens;
        this.challenges = challenges;
        this.sessions = sessions;
        this.settings = settings;
    }

    public String profile(UUID userId) {
        requireUser(userId);
        if (users.findById(userId).filter(user -> user.active()).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
        requireConfigured();
        return url(tokens.issue(userId, "", PROFILE_TTL, true));
    }

    String initial(UUID userId) {
        requireUser(userId);
        return initial(userId, "");
    }

    String challenge(String id, String deviceId) {
        LoginChallenge challenge = challenges
                .find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Challenge expired"));
        if (deviceId == null || !deviceId.equals(challenge.deviceId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge belongs to another device");
        if (challenge.status() != ChallengeStatus.BINDING_REQUIRED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unexpected challenge state");
        return initial(challenge.userId(), id);
    }

    private String initial(UUID userId, String challengeId) {
        if (binding.chatId(userId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Telegram is already bound");
        requireConfigured();
        return url(tokens.issue(userId, challengeId));
    }

    public boolean linked(UUID userId) {
        return binding.chatId(userId).isPresent();
    }

    public void unbind(UUID userId) {
        requireUser(userId);
        sessions.revokeUser(userId);
        tokens.invalidate(userId);
        binding.unbind(userId);
    }

    private void requireUser(UUID userId) {
        users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String url(String token) {
        return "https://t.me/" + settings.botUsername() + "?start=" + token;
    }

    private void requireConfigured() {
        if (!settings.configured() || !settings.botUsername().matches("[A-Za-z0-9_]{5,32}"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram binding is unavailable");
    }
}
