package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.TelegramBinding;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class LoginChallengeActions {
    private final TelegramBinding binding;
    private final DeviceTrust devices;
    private final ChallengeStore challenges;
    private final TelegramChallengeSender telegram;
    private final RefreshSessionStore sessions;

    LoginChallengeActions(
            TelegramBinding binding,
            DeviceTrust devices,
            ChallengeStore challenges,
            TelegramChallengeSender telegram,
            RefreshSessionStore sessions) {
        this.binding = binding;
        this.devices = devices;
        this.challenges = challenges;
        this.telegram = telegram;
        this.sessions = sessions;
    }

    public ChallengeStatus status(String id, String deviceId) {
        LoginChallenge challenge = challenges.find(id).orElse(null);
        if (challenge == null) {
            return ChallengeStatus.EXPIRED;
        }
        requireDevice(challenge, deviceId);
        return challenge.status();
    }

    public String confirmEmail(String id, String deviceId, String code) {
        LoginChallenge challenge = requireChallenge(id, deviceId, ChallengeStatus.PENDING_EMAIL);
        if (!challenges.confirmEmail(id, deviceId, code, ChallengeStatus.APPROVED)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid confirmation code");
        }
        return "";
    }

    public void approveTelegram(String id, long chatId) {
        LoginChallenge challenge = challenges.find(id).orElse(null);
        if (challenge == null
                || challenge.status() != ChallengeStatus.PENDING_TELEGRAM
                || binding.chatId(challenge.userId()).orElse(-1) != chatId) {
            return;
        }
        challenges.transition(id, ChallengeStatus.PENDING_TELEGRAM, ChallengeStatus.APPROVED);
    }

    public void rejectTelegram(String id, long chatId) {
        LoginChallenge challenge = challenges.find(id).orElse(null);
        if (challenge == null
                || challenge.status() != ChallengeStatus.PENDING_TELEGRAM
                || binding.chatId(challenge.userId()).orElse(-1) != chatId) {
            return;
        }
        if (challenges.transition(id, ChallengeStatus.PENDING_TELEGRAM, ChallengeStatus.REJECTED)) {
            devices.block(
                    challenge.userId(),
                    new SessionMetadata(challenge.deviceId(), challenge.userAgent(), challenge.ip()));
            sessions.revokeDevice(challenge.userId(), challenge.deviceId());
        }
    }

    public void resendTelegram(String id, String deviceId) {
        LoginChallenge challenge = requireChallenge(id, deviceId, ChallengeStatus.PENDING_TELEGRAM);
        if (!challenges.mayResend(id)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before resending");
        }
        long chatId = binding.chatId(challenge.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Telegram is not bound"));
        telegram.send(chatId, challenge);
    }

    private LoginChallenge requireChallenge(String id, String deviceId, ChallengeStatus expected) {
        LoginChallenge challenge = challenges
                .find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Challenge expired"));
        requireDevice(challenge, deviceId);
        if (challenge.status() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unexpected challenge state");
        }
        return challenge;
    }

    private void requireDevice(LoginChallenge challenge, String deviceId) {
        if (deviceId == null || !deviceId.equals(challenge.deviceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge belongs to another device");
        }
    }
}
