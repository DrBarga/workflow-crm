package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock
    private RefreshSessionStore sessions;

    @Mock
    private AuthenticationUserQuery users;

    private DeviceSessionService service;

    @BeforeEach
    void setUp() {
        service = new DeviceSessionService(sessions, users);
    }

    @Test
    void listsNewestSessionFirst() {
        UUID userId = UUID.randomUUID();
        var older = new DeviceSession("older", "Browser A", "127.0.0.1", Instant.parse("2026-09-17T10:00:00Z"));
        var newer = new DeviceSession("newer", "Browser B", "127.0.0.2", Instant.parse("2026-09-17T11:00:00Z"));
        when(sessions.findByUser(userId)).thenReturn(List.of(older, newer));

        assertThat(service.list(userId)).containsExactly(newer, older);
    }

    @Test
    void doesNotExposeWhetherAnotherUsersSessionExists() {
        UUID userId = UUID.randomUUID();
        when(sessions.revoke(userId, "session")).thenReturn(false);

        assertThatThrownBy(() -> service.revoke(userId, "session"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void administratorCanRevokeOneOrAllSessionsOfAnExistingUser() {
        UUID administratorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(users.findById(administratorId)).thenReturn(Optional.of(user(administratorId, "SUPERADMIN", true)));
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, "MANAGER", true)));
        when(sessions.revoke(userId, "session")).thenReturn(true);

        service.revokeAsAdministrator(administratorId, userId, "session");
        service.revokeAllAsAdministrator(administratorId, userId);

        verify(sessions).revoke(userId, "session");
        verify(sessions).revokeUser(userId);
    }

    @Test
    void administratorPermissionUsesCurrentActiveAccount() {
        UUID administratorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(users.findById(administratorId)).thenReturn(Optional.of(user(administratorId, "MANAGER", true)));

        assertThatThrownBy(() -> service.revokeAllAsAdministrator(administratorId, userId))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(sessions);
    }

    @Test
    void administratorCannotOperateOnUnknownUser() {
        UUID administratorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(users.findById(administratorId)).thenReturn(Optional.of(user(administratorId, "SUPERADMIN", true)));

        assertThatThrownBy(() -> service.revokeAllAsAdministrator(administratorId, userId))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(sessions);
    }

    private AuthenticationUser user(UUID id, String role, boolean active) {
        return new AuthenticationUser(id, "hash", "User", role, null, active);
    }
}
