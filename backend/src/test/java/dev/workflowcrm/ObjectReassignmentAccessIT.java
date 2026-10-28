package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectReassignmentAccessIT extends ObjectReassignmentTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void otherRolesCannotReassignEvenBeforeParsing(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        reassign(actor(role), object, Map.of()).andExpect(status().isForbidden());
        assertThat(owner(object)).isEqualTo(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR"})
    void allowedRolesReassignInTheirScope(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        reassign(actor(role), object, colleague).andExpect(status().isOk());
        assertThat(owner(object)).isEqualTo(colleague);
    }

    @Test
    void leadCannotReassignForeignRegionButDirectorCan() throws Exception {
        UUID target = UUID.randomUUID();
        user(target, "MANAGER", otherRegion);
        UUID object = add("Чужий об’єкт", outsider, otherRegion);
        reassign(lead, object, target).andExpect(status().isForbidden());
        reassign(director, object, target).andExpect(status().isOk());
        assertThat(owner(object)).isEqualTo(target);
    }

    @Test
    void leadCanReassignWithinAdditionalAssignedRegion() throws Exception {
        UserRegionTestData.assign(jdbc, lead, otherRegion);
        UUID target = UUID.randomUUID();
        user(target, "MANAGER", otherRegion);
        UUID object = add("Об’єкт області", outsider, otherRegion);
        reassign(lead, object, target).andExpect(status().isOk());
    }

    @Test
    void directorStillCannotMoveOwnershipAcrossRegions() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var error = response(reassign(director, object, outsider).andExpect(status().isUnprocessableEntity()));
        assertThat(error.path("violations").get(0).path("field").asText()).isEqualTo("managerId");
        assertThat(owner(object)).isEqualTo(manager);
    }

    @Test
    void inactiveActorCannotUseStaleSessionRole() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update("update users set status='DISABLED' where id=?", lead);
        reassign(lead, object, colleague).andExpect(status().isForbidden());
    }

    @Test
    void changedActorRoleIsTakenFromDatabase() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update("update users set role_id=(select id from roles where code='CEO') where id=?", lead);
        reassign(lead, object, colleague).andExpect(status().isForbidden());
    }

    @Test
    void missingOrDeletedObjectReturns404() throws Exception {
        reassign(lead, UUID.randomUUID(), Map.of("managerId", colleague, "version", 0))
                .andExpect(status().isNotFound());
        UUID object = add("Об’єкт", manager, region);
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id=?", object));
        reassign(director, object, Map.of("managerId", colleague, "version", 0)).andExpect(status().isNotFound());
    }
}
