package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceSessionService {

    private final RefreshSessionStore sessions;
    private final AuthenticationUserQuery users;

    public DeviceSessionService(RefreshSessionStore sessions, AuthenticationUserQuery users) {
        this.sessions = sessions;
        this.users = users;
    }

    public List<DeviceSession> list(UUID userId) {
        return sessions.findByUser(userId).stream()
                .sorted(Comparator.comparing(DeviceSession::lastLoginAt).reversed())
                .toList();
    }

    public void revoke(UUID userId, String sessionId) {
        if (!sessions.revoke(userId, sessionId)) {
            throw sessionNotFound();
        }
    }

    public void revokeAsAdministrator(UUID administratorId, UUID userId, String sessionId) {
        requireAdministrator(administratorId);
        requireUser(userId);
        revoke(userId, sessionId);
    }

    public void revokeAllAsAdministrator(UUID administratorId, UUID userId) {
        requireAdministrator(administratorId);
        requireUser(userId);
        sessions.revokeUser(userId);
    }

    private void requireAdministrator(UUID administratorId) {
        if (users.findById(administratorId)
                .filter(user -> user.active())
                .filter(user -> "SUPERADMIN".equals(user.role()))
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void requireUser(UUID userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private ResponseStatusException sessionNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
    }
}
