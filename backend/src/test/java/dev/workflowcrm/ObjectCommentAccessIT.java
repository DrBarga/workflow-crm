package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCommentAccessIT extends ObjectCommentTestSupport {
    @Test
    void readersFollowFullVisibilityWithContractManagerNetworkException() throws Exception {
        UUID own = add("Own", manager, region), foreign = add("Foreign", outsider, otherRegion);
        seed(own, manager, "own");
        seed(foreign, outsider, "foreign");
        for (UUID actor : List.of(manager, lead, director, ceo, contract, admin))
            assertThat(comments(actor, own, Map.of()).path("items").size()).isEqualTo(1);
        for (UUID actor : List.of(director, ceo, contract, admin))
            assertThat(comments(actor, foreign, Map.of())
                            .path("items")
                            .get(0)
                            .path("text")
                            .asText())
                    .isEqualTo("foreign");
        for (UUID actor : List.of(manager, lead))
            commentRequest(actor, foreign, Map.of()).andExpect(status().isForbidden());
        commentRequest(colleague, own, Map.of()).andExpect(status().isForbidden());
        commentRequest(manager, UUID.randomUUID(), Map.of()).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER"})
    void permittedRolesCreateEditAndDeleteTheirOwnComments(String role) throws Exception {
        UUID actor =
                switch (role) {
                    case "MANAGER" -> manager;
                    case "REGIONAL_MANAGER" -> lead;
                    case "DEVELOPMENT_DIRECTOR" -> director;
                    default -> contract;
                };
        UUID object = add("Writable", manager, region);
        var created = comment(actor, object, "first");
        assertThat(created.path("author").path("id").asText()).isEqualTo(actor.toString());
        patchComment(actor, object, id(created), Map.of("text", "second", "version", 0))
                .andExpect(status().isOk());
        removeComment(actor, object, id(created)).andExpect(status().isNoContent());
    }

    @Test
    void directorAndContractManagerWriteOutsideTheirRegionAndBeforeContractStatus() throws Exception {
        UUID foreign = add("Network", outsider, otherRegion);
        comment(director, foreign, "director");
        comment(contract, foreign, "contract at FOUND");
        assertThat(comments(contract, foreign, Map.of()).path("items").size()).isEqualTo(2);
        postComment(manager, foreign, Map.of("text", "denied")).andExpect(status().isForbidden());
        postComment(lead, foreign, Map.of("text", "denied")).andExpect(status().isForbidden());
    }

    @Test
    void leadReadsAndWritesAdditionalAssignedRegions() throws Exception {
        UUID child = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values(?,'CHILD','Child')", child);
        UserRegionTestData.assign(jdbc, lead, child);
        UUID childManager = UUID.randomUUID();
        user(childManager, "MANAGER", child);
        UUID object = add("Child", childManager, child);
        comment(lead, object, "regional");
        assertThat(comments(lead, object, Map.of()).path("items").size()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEO", "SUPERADMIN"})
    void readOnlyRolesCannotCreateEditOrDeleteEvenOwnLegacyComments(String role) throws Exception {
        UUID actor = role.equals("CEO") ? ceo : admin;
        UUID object = add("Readonly", manager, region);
        UUID own = seed(object, actor, "legacy");
        postComment(actor, object, Map.of("text", "denied")).andExpect(status().isForbidden());
        postComment(actor, object, Map.of()).andExpect(status().isForbidden());
        patchComment(actor, object, own, Map.of("text", "denied", "version", 0)).andExpect(status().isForbidden());
        removeComment(actor, object, own).andExpect(status().isForbidden());
        assertThat(comments(actor, object, Map.of()).path("items").size()).isEqualTo(1);
    }

    @Test
    void authorsLoseMutationAccessAfterReassignmentOrDeactivation() throws Exception {
        UUID object = add("Reassigned", manager, region);
        var created = comment(manager, object, "original");
        jdbc.update("update objects set manager_id=?,version=version+1 where id=?", colleague, object);
        patchComment(manager, object, id(created), Map.of("text", "denied", "version", 0))
                .andExpect(status().isForbidden());
        removeComment(manager, object, id(created)).andExpect(status().isForbidden());
        commentRequest(manager, object, Map.of()).andExpect(status().isForbidden());
        patchComment(colleague, object, id(created), Map.of("text", "denied", "version", 0))
                .andExpect(status().isForbidden());
        var colleagueComment = comment(colleague, object, "colleague");
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        postComment(colleague, object, Map.of("text", "denied")).andExpect(status().isForbidden());
        patchComment(colleague, object, id(colleagueComment), Map.of("text", "denied", "version", 0))
                .andExpect(status().isForbidden());
        removeComment(colleague, object, id(colleagueComment)).andExpect(status().isForbidden());
        commentRequest(colleague, object, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void otherAuthorsCannotEditOrDeleteVisibleComments() throws Exception {
        UUID object = add("Authors", manager, region);
        var created = comment(manager, object, "original");
        for (UUID actor : List.of(lead, director, contract)) {
            patchComment(actor, object, id(created), Map.of("text", "denied", "version", 0))
                    .andExpect(status().isForbidden());
            removeComment(actor, object, id(created)).andExpect(status().isForbidden());
        }
        assertThat(comments(manager, object, Map.of())
                        .path("items")
                        .get(0)
                        .path("text")
                        .asText())
                .isEqualTo("original");
    }
}
