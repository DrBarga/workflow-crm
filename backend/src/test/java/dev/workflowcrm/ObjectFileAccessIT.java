package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectFileAccessIT extends ObjectFileTestSupport {
    @Test
    void contractCategoryIsExcludedBeforePaginationAndDirectAccessIsDeniedToAdministrator() throws Exception {
        UUID object = add("Privacy", manager, region);
        var contractFile = uploaded(contract, object, "CONTRACTS");
        var regular = uploaded(manager, object, "MEDIA");
        var visible = filePage(admin, object, Map.of("limit", "1"));
        assertThat(visible.path("items").size()).isEqualTo(1);
        assertThat(visible.path("items").get(0).path("id")).isEqualTo(regular.path("id"));
        assertThat(visible.path("hasMore").asBoolean()).isFalse();
        link(admin, object, fileId(contractFile)).andExpect(status().isForbidden());
        for (UUID reader : List.of(manager, lead, director, ceo, contract)) {
            assertThat(filePage(reader, object, Map.of()).path("items").size()).isEqualTo(2);
            link(reader, object, fileId(contractFile)).andExpect(status().isOk());
        }
        for (UUID reader : List.of(colleague, outsider)) {
            fileList(reader, object, Map.of()).andExpect(status().isForbidden());
            link(reader, object, fileId(contractFile)).andExpect(status().isForbidden());
        }
        deleteFile(admin, object, fileId(contractFile)).andExpect(status().isForbidden());
    }

    @Test
    void uploadPermissionsUseCurrentOwnershipRegionAndRoleEvenForContractRoleBeforeContractStatus() throws Exception {
        UUID object = add("Upload", manager, region);
        for (UUID actor : List.of(manager, lead, director, contract)) uploaded(actor, object, "OTHER");
        for (UUID actor : List.of(ceo, admin, colleague, outsider))
            upload(actor, object, category("OTHER"), "document.pdf", PDF).andExpect(status().isForbidden());
        UUID remote = add("Remote", outsider, otherRegion);
        upload(lead, remote, category("OTHER"), "document.pdf", PDF).andExpect(status().isForbidden());
        uploaded(director, remote, "CONTRACTS");
        uploaded(contract, remote, "CONTRACTS");
        assertThat(count("files")).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "CONTRACT_MANAGER", "SUPERADMIN"})
    void allowedDeleteRolesCanRemoveFilesWithinTheirScope(String role) throws Exception {
        UUID object = add("Delete " + role, manager, region);
        UUID actor =
                switch (role) {
                    case "MANAGER" -> manager;
                    case "REGIONAL_MANAGER" -> lead;
                    case "CONTRACT_MANAGER" -> contract;
                    default -> admin;
                };
        var file = uploaded(contract, object, "OTHER");
        deleteFile(actor, object, fileId(file)).andExpect(status().isNoContent());
        assertThat(filePage(manager, object, Map.of()).path("items").size()).isZero();
    }

    @Test
    void directorAndCeoCannotDeleteAndContractManagerCannotDeleteOtherAuthorsFile() throws Exception {
        UUID object = add("Forbidden deletion", manager, region);
        var file = uploaded(manager, object, "OTHER");
        for (UUID actor : List.of(director, ceo, contract, colleague, outsider))
            deleteFile(actor, object, fileId(file)).andExpect(status().isForbidden());
        UUID remote = add("Remote", outsider, otherRegion);
        var remoteFile = uploaded(outsider, remote, "OTHER");
        deleteFile(lead, remote, fileId(remoteFile)).andExpect(status().isForbidden());
        assertThat(count("object_history")).isEqualTo(2);
    }

    @Test
    void previousManagerMayDeleteOwnFileButCannotReadObjectOrDeleteOtherAuthorsAfterReassignment() throws Exception {
        UUID object = add("Reassigned", manager, region);
        var own = uploaded(manager, object, "OTHER");
        var other = uploaded(contract, object, "OTHER");
        jdbc.update("update objects set manager_id=? where id=?", colleague, object);
        link(manager, object, fileId(own)).andExpect(status().isForbidden());
        deleteFile(manager, object, fileId(other)).andExpect(status().isForbidden());
        deleteFile(manager, object, fileId(own)).andExpect(status().isNoContent());
        deleteFile(colleague, object, fileId(other)).andExpect(status().isNoContent());
    }

    @Test
    void disabledUsersAndChangedDatabaseRolesCannotReuseOldPermission() throws Exception {
        UUID object = add("Current rights", manager, region);
        var file = uploaded(manager, object, "OTHER");
        var cookie = cookie(manager);
        jdbc.update("update users set role_id=(select id from roles where code='CEO') where id=?", manager);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/objects/" + object + "/files/" + fileId(file))
                        .cookie(cookie, csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        fileList(manager, object, Map.of()).andExpect(status().isForbidden());
        link(manager, object, fileId(file)).andExpect(status().isForbidden());
    }

    @Test
    void regionalManagerIncludesAssignedRegionsButNotOthers() throws Exception {
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values(?,?,'Parent')", parent, parent.toString());
        UserRegionTestData.replace(jdbc, lead, parent, region);
        UUID object = add("Child", manager, region);
        var file = uploaded(lead, object, "CONTRACTS");
        link(lead, object, fileId(file)).andExpect(status().isOk());
        deleteFile(lead, object, fileId(file)).andExpect(status().isNoContent());
        UUID remote = add("Other", outsider, otherRegion);
        fileList(lead, remote, Map.of()).andExpect(status().isForbidden());
    }
}
