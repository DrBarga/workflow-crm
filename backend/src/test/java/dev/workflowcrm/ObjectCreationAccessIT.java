package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCreationAccessIT extends ObjectApiTestSupport {
    @Test
    void regionalLeadAssignsAnActiveManagerOfOwnRegion() throws Exception {
        var body = body();
        body.put("managerId", colleague.toString());
        create(lead, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerId").value(colleague.toString()))
                .andExpect(jsonPath("$.authorId").value(lead.toString()));
    }

    @Test
    void leadMustChooseManagerAndCannotAssignForeignInactiveOrNonManagerUser() throws Exception {
        create(lead, key(), body()).andExpect(status().isUnprocessableEntity());
        for (var id : java.util.List.of(outsider, lead, UUID.randomUUID())) {
            var body = body();
            body.put("managerId", id.toString());
            create(lead, key(), body).andExpect(status().isForbidden());
        }
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        var body = body();
        body.put("managerId", colleague.toString());
        create(lead, key(), body).andExpect(status().isForbidden());
    }

    @Test
    void managerCannotAssignAnotherManagerOrChooseUnassignedRegion() throws Exception {
        for (var id : java.util.List.of(colleague, outsider)) {
            var body = body();
            body.put("managerId", id.toString());
            create(manager, key(), body).andExpect(status().isForbidden());
        }
        var body = body();
        body.put("regionId", otherRegion.toString());
        create(manager, key(), body).andExpect(status().isForbidden());
        assertThat(count("objects")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void otherRolesHaveNoImplicitCreationPermission(String role) throws Exception {
        jdbc.update("update users set role_id=(select id from roles where code=?) where id=?", role, manager);
        create(manager, key(), body()).andExpect(status().isForbidden());
    }

    @Test
    void checksCurrentUserStatusAndRegionRatherThanJwtClaims() throws Exception {
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        create(manager, key(), body()).andExpect(status().isForbidden());
        jdbc.update("update users set status='ACTIVE' where id=?", manager);
        UserRegionTestData.replace(jdbc, manager);
        create(manager, key(), body()).andExpect(status().isForbidden());
    }
}
