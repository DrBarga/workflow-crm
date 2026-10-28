package dev.workflowcrm.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoginFlowService {
    private final LoginSessionFlow sessions;
    private final LoginChallengeActions challenges;
    private final TelegramLinkIssuer links;
    private final TelegramBindingRequests requests;
    private final TelegramBindingDecisions decisions;

    public LoginFlowService(
            LoginSessionFlow sessions,
            LoginChallengeActions challenges,
            TelegramLinkIssuer links,
            TelegramBindingRequests requests,
            TelegramBindingDecisions decisions) {
        this.sessions = sessions;
        this.challenges = challenges;
        this.links = links;
        this.requests = requests;
        this.decisions = decisions;
    }

    public LoginStart start(LoginCredentials credentials, SessionMetadata metadata) {
        return sessions.start(credentials, metadata);
    }

    public LoginResult complete(String id, String deviceId) {
        return sessions.complete(id, deviceId);
    }

    public ChallengeStatus status(String id, String deviceId) {
        return challenges.status(id, deviceId);
    }

    public String confirmEmail(String id, String deviceId, String code) {
        return challenges.confirmEmail(id, deviceId, code);
    }

    public void approveTelegram(String id, long chatId) {
        challenges.approveTelegram(id, chatId);
    }

    public void rejectTelegram(String id, long chatId) {
        challenges.rejectTelegram(id, chatId);
    }

    public void resendTelegram(String id, String deviceId) {
        challenges.resendTelegram(id, deviceId);
    }

    public String bindingLink(String id, String deviceId) {
        return links.challenge(id, deviceId);
    }

    public String bindingLink(UUID userId) {
        return links.initial(userId);
    }

    public boolean linked(UUID userId) {
        return links.linked(userId);
    }

    public void unbind(UUID userId) {
        links.unbind(userId);
    }

    public Optional<TelegramBindingPreview> previewTelegramBinding(String token) {
        return requests.preview(token);
    }

    public boolean telegramChatInUse(String token, long chatId) {
        return requests.chatInUse(token, chatId);
    }

    public TelegramBindingDecision confirmTelegramBinding(String token, long chatId) {
        return decisions.confirm(token, chatId);
    }

    public TelegramBindingDecision rejectTelegramBinding(String token) {
        return decisions.reject(token);
    }
}
