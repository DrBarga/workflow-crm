package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectExportAccessIT extends ObjectExportTestSupport {
    @Test
    void regionalExportContainsOnlyExplicitlyAssignedRegions() throws Exception {
        var child = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values(?,'EXPORT-CHILD','Дочірній')", child);
        UserRegionTestData.assign(jdbc, lead, child);
        add("Свій", manager, region);
        add("Дочірній", manager, child);
        add("Чужий", outsider, otherRegion);
        assertThat(csv(lead, Map.of())).contains("Свій", "Дочірній").doesNotContain("Чужий");
        assertThat(csv(lead, Map.of("regionId", otherRegion.toString())).lines().count())
                .isEqualTo(1);
    }

    @Test
    void networkRolesExportEveryRegion() throws Exception {
        add("Свій", manager, region);
        add("Чужий", outsider, otherRegion);
        for (UUID actor : List.of(director, ceo, admin))
            assertThat(csv(actor, Map.of())).contains("Свій", "Чужий");
    }

    @Test
    void managersContractDisabledAndMissingUsersCannotExport() throws Exception {
        for (UUID actor : List.of(manager, colleague, outsider, contract, UUID.randomUUID()))
            export(actor, Map.of("format", "CSV")).andExpect(status().isForbidden());
        jdbc.update("update users set status='DISABLED' where id=?", director);
        export(director, Map.of("format", "CSV")).andExpect(status().isForbidden());
        assertThat(count("object_exports")).isZero();
        assertThat(count("object_export_audit")).isZero();
    }

    @Test
    void jobAndDownloadArePrivateEvenToOtherNetworkRoles() throws Exception {
        UUID id = ready(director, "CSV");
        for (UUID actor : List.of(ceo, admin, lead)) {
            exportStatus(actor, id).andExpect(status().isNotFound());
            exportDownload(actor, id).andExpect(status().isNotFound());
        }
        exportStatus(manager, id).andExpect(status().isForbidden());
        exportDownload(contract, id).andExpect(status().isForbidden());
    }

    @Test
    void permissionChangeBeforeProcessingFailsWithoutPublishing() throws Exception {
        UUID id = enqueue(director, "CSV", Map.of());
        jdbc.update("update users set role_id=(select id from roles where code='MANAGER') where id=?", director);
        UserRegionTestData.replace(jdbc, director, region);
        assertThat(exportProcessor.processNext()).isTrue();
        assertThat(jdbc.queryForObject("select state from object_exports where id=?", String.class, id))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select error from object_exports where id=?", String.class, id))
                .isEqualTo("PERMISSIONS_CHANGED");
        exportDownload(director, id).andExpect(status().isForbidden());
        assertThat(count("object_export_audit")).isEqualTo(1);
    }

    @Test
    void permissionChangeAfterReadyRevokesLinkIssuance() throws Exception {
        UUID id = ready(lead, "CSV");
        UserRegionTestData.replace(jdbc, lead, otherRegion);
        exportStatus(lead, id).andExpect(status().isForbidden());
        exportDownload(lead, id).andExpect(status().isForbidden());
    }

    @Test
    void databaseRoleOverridesJwtAndNoRegionProducesHeaders() throws Exception {
        var stale = cookie(manager);
        jdbc.update(
                "update users set role_id=(select id from roles where code='DEVELOPMENT_DIRECTOR') where id=?",
                manager);
        mvc.perform(post("/api/objects/export")
                        .cookie(stale, csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isAccepted());
        exportProcessor.processNext();
        UserRegionTestData.replace(jdbc, lead);
        assertThat(csv(lead, Map.of()).lines().count()).isEqualTo(1);
    }

    @Test
    void csrfAndAuthenticationAreRequired() throws Exception {
        mvc.perform(post("/api/objects/export")
                        .cookie(cookie(director))
                        .contentType("application/json")
                        .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/objects/export")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/objects/exports/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
