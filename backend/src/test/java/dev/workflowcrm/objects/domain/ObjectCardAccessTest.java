package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ObjectCardAccessTest {
    @Test
    void roleWithoutSectionPermissionIsRejectedBeforeObjectLookup() {
        var users = mock(ObjectAssignmentQuery.class);
        UUID actor = UUID.randomUUID();
        when(users.member(actor))
                .thenReturn(Optional.of(new ObjectAssignmentQuery.Member(actor, "NO_OBJECT_ACCESS", null, true)));
        assertThatThrownBy(() -> new ObjectCardAccess(users).require(actor)).isInstanceOf(AccessDeniedException.class);
    }
}
