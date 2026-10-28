package dev.workflowcrm.auth.domain;

import dev.workflowcrm.audit.api.SecurityUnlockAudit;
import dev.workflowcrm.audit.api.SecurityUnlockAudit.Action;
import dev.workflowcrm.audit.api.SecurityUnlockAudit.Outcome;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSecurityService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminSecurityService.class);
    private final AuthenticationUserQuery users;
    private final DeviceTrust devices;
    private final LoginAttemptTracker attempts;
    private final RefreshSessionStore sessions;
    private final SecurityUnlockAudit audit;
    private final AdminActionRateLimit rateLimit;

    public AdminSecurityService(
            AuthenticationUserQuery users,
            DeviceTrust devices,
            LoginAttemptTracker attempts,
            RefreshSessionStore sessions,
            SecurityUnlockAudit audit,
            AdminActionRateLimit rateLimit) {
        this.users = users;
        this.devices = devices;
        this.attempts = attempts;
        this.sessions = sessions;
        this.audit = audit;
        this.rateLimit = rateLimit;
    }

    public List<BlockedDevice> blockedDevices(UUID administratorId, UUID userId) {
        requireAdministrator(administratorId);
        requireUser(userId);
        try {
            return devices.blockedDevices(userId).stream()
                    .sorted(Comparator.comparing(
                                    BlockedDevice::blockedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(BlockedDevice::id))
                    .toList();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void unblockDevice(UUID administratorId, UUID userId, UUID blockId) {
        BlockedDevice device = blockedDevices(administratorId, userId).stream()
                .filter(candidate -> candidate.id().equals(blockId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocked device not found"));
        runAudited(administratorId, userId, Action.DEVICE_UNBLOCK, blockId, () -> {
            sessions.revokeDevice(userId, device.deviceId());
            return devices.unblock(userId, device);
        });
    }

    public LoginLock loginLock(UUID administratorId, UUID userId) {
        String email = authorizedEmail(administratorId, userId);
        try {
            return attempts.lock(email);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void unlockLogin(UUID administratorId, UUID userId) {
        String email = authorizedEmail(administratorId, userId);
        try {
            if (!attempts.isBlocked(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Login is not temporarily blocked");
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        runAudited(administratorId, userId, Action.LOGIN_UNLOCK, null, () -> attempts.unlock(email));
    }

    private void runAudited(UUID administratorId, UUID userId, Action action, UUID blockId, BooleanSupplier unlock) {
        try {
            rateLimit.reserve(administratorId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        UUID eventId;
        try {
            eventId = audit.request(administratorId, userId, action, blockId);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        boolean applied;
        try {
            applied = unlock.getAsBoolean();
        } catch (RuntimeException exception) {
            try {
                audit.complete(eventId, Outcome.FAILED);
            } catch (RuntimeException auditException) {
                LOG.error("Unable to complete security unlock audit event {}", eventId);
            }
            throw unavailable(exception);
        }
        try {
            audit.complete(eventId, applied ? Outcome.APPLIED : Outcome.UNCHANGED);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        if (!applied) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Block has changed; reload before unlocking");
        }
    }

    private String authorizedEmail(UUID administratorId, UUID userId) {
        requireAdministrator(administratorId);
        return users.email(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireAdministrator(UUID administratorId) {
        if (users.findById(administratorId)
                .filter(user -> user.active() && "SUPERADMIN".equals(user.role()))
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void requireUser(UUID userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private ResponseStatusException unavailable(RuntimeException exception) {
        LOG.error(
                "Security administration is unavailable: {}",
                exception.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Security administration is unavailable");
    }
}
