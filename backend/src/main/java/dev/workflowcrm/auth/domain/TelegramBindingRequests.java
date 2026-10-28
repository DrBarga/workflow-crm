package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.TelegramBinding;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class TelegramBindingRequests {
    private final BindingTokens tokens;
    private final ChallengeStore challenges;
    private final TelegramBinding binding;
    private final AuthenticationUserQuery users;

    TelegramBindingRequests(
            BindingTokens tokens, ChallengeStore challenges, TelegramBinding binding, AuthenticationUserQuery users) {
        this.tokens = tokens;
        this.challenges = challenges;
        this.binding = binding;
        this.users = users;
    }

    Optional<BindingRequest> find(String token) {
        return tokens.find(token).filter(this::valid);
    }

    Optional<TelegramBindingPreview> preview(String token) {
        return find(token).flatMap(request -> binding.email(request.userId()).map(TelegramBindingPreview::new));
    }

    boolean chatInUse(String token, long chatId) {
        return find(token)
                .map(request -> binding.belongsToAnotherUser(request.userId(), chatId))
                .orElse(false);
    }

    boolean valid(BindingRequest request) {
        if (users.findById(request.userId()).filter(user -> user.active()).isEmpty()) return false;
        if (request.challengeId().isBlank()) return true;
        LoginChallenge challenge = challenges.find(request.challengeId()).orElse(null);
        return challenge != null
                && challenge.userId().equals(request.userId())
                && challenge.status() == ChallengeStatus.BINDING_REQUIRED;
    }
}
