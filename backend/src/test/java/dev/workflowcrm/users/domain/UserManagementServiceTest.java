package dev.workflowcrm.users.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class UserManagementServiceTest {
    private final UserManagementStore store = mock(UserManagementStore.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final InitialCredential credentials = mock(InitialCredential.class);
    private final UserRegionPolicy regions = mock(UserRegionPolicy.class);
    private final UserInvitationService invitations = mock(UserInvitationService.class);
    private final UserManagementService service =
            new UserManagementService(store, current, credentials, new UserAccessEditor(store, regions), invitations);
    private final UUID actorId = UUID.randomUUID();
    private final User actor = mock(User.class);
    private final Role role = mock(Role.class);

    @BeforeEach
    void setUp() {
        when(current.id()).thenReturn(actorId);
        when(store.find(actorId)).thenReturn(Optional.of(actor));
        when(actor.getRole()).thenReturn(role);
        when(actor.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(role.getCode()).thenReturn(RoleCode.SUPERADMIN);
    }

    @Test
    void deniesNonAdministratorBeforeReadingUserList() {
        when(role.getCode()).thenReturn(RoleCode.MANAGER);
        assertThatThrownBy(() -> service.list(null, null, null, 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> org.assertj.core.api.Assertions.assertThat(
                                e.getStatusCode().value())
                        .isEqualTo(403));
        verify(store, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void forbidsSelfDeactivationBeforeChangingOrSavingAccount() {
        when(store.lock(actorId)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.update(actorId, null, null, false, UserStatus.DISABLED, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> org.assertj.core.api.Assertions.assertThat(
                                e.getStatusCode().value())
                        .isEqualTo(409));
        verify(actor, never()).changeAccess(any(), any(), any());
        verify(store, never()).save(any());
    }

    @Test
    void rejectsStaleClientVersionBeforeMutation() {
        UUID targetId = UUID.randomUUID();
        User target = mock(User.class);
        when(store.lock(targetId)).thenReturn(Optional.of(target));
        when(target.getVersion()).thenReturn(2L);
        assertThatThrownBy(() -> service.update(targetId, null, null, false, UserStatus.DISABLED, 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> org.assertj.core.api.Assertions.assertThat(
                                e.getStatusCode().value())
                        .isEqualTo(409));
        verify(target, never()).changeAccess(any(), any(), any());
    }

    @Test
    void rejectsUnknownRegionBeforeIssuingCredentialOrSaving() {
        UUID regionId = UUID.randomUUID();
        when(store.role(RoleCode.MANAGER)).thenReturn(Optional.of(role));
        when(store.region(regionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() ->
                        service.create("test@example.com", "Test User", RoleCode.MANAGER, java.util.List.of(regionId)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> org.assertj.core.api.Assertions.assertThat(
                                e.getStatusCode().value())
                        .isEqualTo(400));
        verifyNoInteractions(credentials);
        verify(store, never()).save(any());
    }
}
