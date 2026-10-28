package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.workflowcrm.audit.api.SecurityUnlockAudit;
import dev.workflowcrm.audit.api.SecurityUnlockAudit.Action;
import dev.workflowcrm.audit.api.SecurityUnlockAudit.Outcome;
import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminSecurityServiceTest {
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();
    private static final BlockedDevice DEVICE = new BlockedDevice(UUID.randomUUID(), "a".repeat(64), "", "", null);

    @Mock
    private AuthenticationUserQuery users;

    @Mock
    private DeviceTrust devices;

    @Mock
    private LoginAttemptTracker attempts;

    @Mock
    private RefreshSessionStore sessions;

    @Mock
    private SecurityUnlockAudit audit;

    @Mock
    private AdminActionRateLimit rateLimit;

    private AdminSecurityService security;

    @BeforeEach
    void setUp() {
        security = new AdminSecurityService(users, devices, attempts, sessions, audit, rateLimit);
    }

    @Test
    void redisFailureLeavesDeviceBlockedAndRecordsFailedRequest() {
        authorizeDevice();
        when(audit.request(ADMIN, USER, Action.DEVICE_UNBLOCK, DEVICE.id())).thenReturn(EVENT);
        doThrow(new DataAccessResourceFailureException("Unavailable"))
                .when(sessions)
                .revokeDevice(USER, DEVICE.deviceId());
        assertStatus(503, () -> security.unblockDevice(ADMIN, USER, DEVICE.id()));
        verify(devices, never()).unblock(USER, DEVICE);
        verify(audit).complete(EVENT, Outcome.FAILED);
    }

    @Test
    void raceWithAnotherAdministratorRecordsUnchangedInsteadOfSuccess() {
        authorizeDevice();
        when(audit.request(ADMIN, USER, Action.DEVICE_UNBLOCK, DEVICE.id())).thenReturn(EVENT);
        when(devices.unblock(USER, DEVICE)).thenReturn(false);
        assertStatus(409, () -> security.unblockDevice(ADMIN, USER, DEVICE.id()));
        verify(audit).complete(EVENT, Outcome.UNCHANGED);
    }

    @Test
    void inaccessibleAdministratorCannotReadOrMutateSecurityState() {
        when(users.findById(ADMIN)).thenReturn(Optional.empty());
        assertStatus(403, () -> security.blockedDevices(ADMIN, USER));
        assertStatus(403, () -> security.unlockLogin(ADMIN, USER));
        verifyNoInteractions(devices, attempts, sessions, audit);
    }

    @Test
    void redisReadFailureDoesNotPretendThatNoDevicesAreBlocked() {
        when(users.findById(ADMIN))
                .thenReturn(Optional.of(new AuthenticationUser(ADMIN, "", "", "SUPERADMIN", null, true)));
        when(users.findById(USER)).thenReturn(Optional.of(new AuthenticationUser(USER, "", "", "MANAGER", null, true)));
        when(devices.blockedDevices(USER)).thenThrow(new DataAccessResourceFailureException("Unavailable"));
        assertStatus(503, () -> security.blockedDevices(ADMIN, USER));
        verifyNoInteractions(sessions, audit);
    }

    private void authorizeDevice() {
        when(users.findById(ADMIN))
                .thenReturn(Optional.of(new AuthenticationUser(ADMIN, "", "", "SUPERADMIN", null, true)));
        when(users.findById(USER)).thenReturn(Optional.of(new AuthenticationUser(USER, "", "", "MANAGER", null, true)));
        when(devices.blockedDevices(USER)).thenReturn(List.of(DEVICE));
    }

    private void assertStatus(int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getStatusCode().value())
                                .isEqualTo(status));
    }
}
