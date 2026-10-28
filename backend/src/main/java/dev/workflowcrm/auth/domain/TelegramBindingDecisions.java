package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.TelegramAccountAlreadyBoundException;
import dev.workflowcrm.users.api.TelegramBinding;
import dev.workflowcrm.users.api.TelegramChatInUseException;
import org.springframework.stereotype.Service;

@Service
class TelegramBindingDecisions {
    private final BindingTokens bindingTokens;
    private final TelegramBindingRequests requests;
    private final TelegramBinding binding;
    private final ChallengeStore challenges;

    TelegramBindingDecisions(
            BindingTokens bindingTokens,
            TelegramBindingRequests requests,
            TelegramBinding binding,
            ChallengeStore challenges) {
        this.bindingTokens = bindingTokens;
        this.requests = requests;
        this.binding = binding;
        this.challenges = challenges;
    }

    public TelegramBindingDecision confirm(String token, long chatId) {
        BindingRequest request = requests.find(token).orElse(null);
        if (request == null) {
            return TelegramBindingDecision.EXPIRED;
        }
        if (!request.replaceExisting() && binding.chatId(request.userId()).isPresent()) {
            return TelegramBindingDecision.ACCOUNT_ALREADY_BOUND;
        }
        if (binding.belongsToAnotherUser(request.userId(), chatId)) {
            return TelegramBindingDecision.CHAT_IN_USE;
        }
        request = bindingTokens.consume(token).filter(requests::valid).orElse(null);
        if (request == null) {
            return TelegramBindingDecision.EXPIRED;
        }
        try {
            if (request.replaceExisting()) binding.replace(request.userId(), chatId);
            else binding.bind(request.userId(), chatId);
        } catch (TelegramChatInUseException exception) {
            return TelegramBindingDecision.CHAT_IN_USE;
        } catch (TelegramAccountAlreadyBoundException exception) {
            return TelegramBindingDecision.ACCOUNT_ALREADY_BOUND;
        }
        if (!request.challengeId().isBlank()) {
            challenges.transition(request.challengeId(), ChallengeStatus.BINDING_REQUIRED, ChallengeStatus.APPROVED);
        }
        return TelegramBindingDecision.BOUND;
    }

    public TelegramBindingDecision reject(String token) {
        BindingRequest request = requests.find(token).orElse(null);
        if (request == null || bindingTokens.consume(token).isEmpty()) {
            return TelegramBindingDecision.EXPIRED;
        }
        if (!request.challengeId().isBlank()) {
            LoginChallenge challenge = challenges.find(request.challengeId()).orElse(null);
            if (challenge != null) {
                challenges.transition(
                        request.challengeId(), ChallengeStatus.BINDING_REQUIRED, ChallengeStatus.REJECTED);
            }
        }
        return TelegramBindingDecision.REJECTED;
    }
}
