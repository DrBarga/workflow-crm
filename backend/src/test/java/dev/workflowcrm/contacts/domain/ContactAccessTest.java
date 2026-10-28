package dev.workflowcrm.contacts.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactAccessTest {
    final UUID author = UUID.randomUUID(), manager = UUID.randomUUID(), region = UUID.randomUUID();
    final Contact contact = new Contact(new ContactDetails("Name", "Role", null, "a@example.com"), author);
    final ContactObject object = new ContactObject(UUID.randomUUID(), "Object", manager, region, "NEW", false, 0);

    @Test
    void linkedObjectScopeGrantsEditingWithoutTransferringDeletionRights() {
        var users = mock(ObjectAssignmentQuery.class);
        var actor = new ContactScope(manager, "MANAGER", List.of(region));
        var shared = new ContactAccess(users);
        assertThat(shared.canEdit(actor, contact, List.of(object))).isTrue();
        assertThat(shared.canDelete(actor)).isFalse();
        assertThat(shared.canEdit(new ContactScope(manager, "CEO", List.of()), contact, List.of(object)))
                .isFalse();
        assertThat(shared.canEdit(
                        new ContactScope(manager, "MANAGER", List.of(UUID.randomUUID())), contact, List.of(object)))
                .isFalse();
        assertThat(shared.canEdit(new ContactScope(author, "MANAGER", List.of(region)), contact, List.of()))
                .isFalse();
        var admin = new ContactScope(manager, "SUPERADMIN", List.of());
        assertThat(shared.canEdit(admin, contact, List.of())).isTrue();
        assertThat(shared.canDelete(admin)).isTrue();
    }

    @Test
    void expandsEveryAssignedRegionalTreeAndRejectsInactiveMember() {
        var users = mock(ObjectAssignmentQuery.class);
        var second = UUID.randomUUID();
        var child = UUID.randomUUID();
        when(users.member(manager))
                .thenReturn(Optional.of(new ObjectAssignmentQuery.Member(
                        manager, "REGIONAL_MANAGER", region, true, "Name", null, List.of(region, second))));
        when(users.regionTree(region)).thenReturn(List.of(region, child));
        when(users.regionTree(second)).thenReturn(List.of(second));
        var access = new ContactAccess(users);
        assertThat(access.require(manager).regions()).containsExactly(region, child, second);
        when(users.member(manager))
                .thenReturn(Optional.of(new ObjectAssignmentQuery.Member(manager, "MANAGER", region, false)));
        assertThatThrownBy(() -> access.require(manager))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
