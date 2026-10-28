package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.workflowcrm.users.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class ObjectDeletionAccessTest {
    private final ObjectAssignmentQuery users = mock(ObjectAssignmentQuery.class);
    private final ObjectAssignmentLock locks = mock(ObjectAssignmentLock.class);
    private final ObjectDeletionAccess access = new ObjectDeletionAccess(users, locks);
    private final UUID actor = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER"})
    void everyOtherRoleIsDenied(String role) {
        when(users.member(actor)).thenReturn(Optional.of(new ObjectAssignmentQuery.Member(actor, role, null, true)));
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void locksAndRechecksAdministratorState() {
        when(users.member(actor))
                .thenReturn(Optional.of(new ObjectAssignmentQuery.Member(actor, "SUPERADMIN", null, true)));
        access.require(actor);
        when(locks.members(Set.of(actor)))
                .thenReturn(Map.of(actor, new ObjectAssignmentQuery.Member(actor, "SUPERADMIN", null, false)));
        assertThatThrownBy(() -> access.lock(actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingActorIsDenied() {
        when(users.member(actor)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activeAdministratorIsAllowed() {
        when(locks.members(Set.of(actor)))
                .thenReturn(Map.of(actor, new ObjectAssignmentQuery.Member(actor, "SUPERADMIN", null, true)));
        assertThatCode(() -> access.lock(actor)).doesNotThrowAnyException();
    }
}
