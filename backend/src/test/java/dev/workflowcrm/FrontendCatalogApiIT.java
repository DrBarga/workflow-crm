package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrontendCatalogApiIT extends ObjectStatusTestSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void catalogsUseActiveEntriesAndUserRegionScope(String role) throws Exception {
        var actor = actor(role);
        for (String path : List.of("/api/data-sources", "/api/contract-types", "/api/file-categories")) {
            var result = read(actor, path);
            assertThat(result.isArray()).isTrue();
            assertThat(result).isNotEmpty();
            for (var item : result)
                assertThat(item.hasNonNull("id") && item.hasNonNull("code") && item.hasNonNull("name"))
                        .isTrue();
        }
        var regions = read(actor, "/api/regions");
        for (var category : read(actor, "/api/file-categories"))
            assertThat(category.has("isRestricted")).isTrue();
        assertThat(regions).hasSize(Set.of("MANAGER", "REGIONAL_MANAGER").contains(role) ? 1 : 2);
        if (role.equals("SUPERADMIN"))
            for (var category : read(actor, "/api/file-categories"))
                assertThat(category.path("isRestricted").asBoolean()).isFalse();
    }

    @Test
    void inactiveDictionaryRowsAreNotReturned() throws Exception {
        UUID source = UUID.randomUUID(), type = UUID.randomUUID(), category = UUID.randomUUID();
        try {
            jdbc.update(
                    "insert into object_data_sources(id,code,name,is_active) values (?,'TEST_HIDDEN','Hidden',false)",
                    source);
            jdbc.update(
                    "insert into contract_types(id,code,name,is_active) values (?,'TEST_HIDDEN','Hidden',false)", type);
            jdbc.update(
                    "insert into file_categories(id,code,name,is_active,is_restricted) values (?,'TEST_HIDDEN','Hidden',false,false)",
                    category);
            for (String path : List.of("/api/data-sources", "/api/contract-types", "/api/file-categories"))
                assertThat(read(manager, path).toString()).doesNotContain("TEST_HIDDEN");
        } finally {
            jdbc.update("delete from file_categories where id=?", category);
            jdbc.update("delete from contract_types where id=?", type);
            jdbc.update("delete from object_data_sources where id=?", source);
        }
    }

    @Test
    void managerPickerIsScopedAndDoesNotExposeAdministrativeData() throws Exception {
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        for (UUID actor : List.of(lead, director, admin)) {
            var result = read(actor, "/api/managers?regionId=" + region);
            assertThat(result.path("content")).hasSize(1);
            var option = result.path("content").get(0);
            assertThat(option.path("id").asText()).isEqualTo(manager.toString());
            assertThat(option.has("email") || option.has("status") || option.has("version"))
                    .isFalse();
        }
        var compatible = read(lead, "/api/users?role=MANAGER&regionId=" + region);
        assertThat(compatible.path("content")).hasSize(1);
        assertThat(compatible.toString()).doesNotContain("email", "password", "lastLogin");
        mvc.perform(get("/api/users")
                        .param("role", "MANAGER")
                        .param("regionId", otherRegion.toString())
                        .cookie(cookie(lead)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users")
                        .param("role", "CEO")
                        .param("regionId", region.toString())
                        .cookie(cookie(director)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").param("role", "MANAGER").cookie(cookie(lead)))
                .andExpect(status().isUnprocessableEntity());
        for (UUID actor : List.of(manager, ceo, contract))
            mvc.perform(get("/api/managers")
                            .param("regionId", region.toString())
                            .cookie(cookie(actor)))
                    .andExpect(status().isForbidden());
        assertThat(read(admin, "/api/users").path("content")).isNotEmpty();
    }

    @Test
    void lookupPaginationAndAuthenticationAreEnforced() throws Exception {
        var first = read(lead, "/api/managers?regionId=" + region + "&size=1");
        var second = read(lead, "/api/managers?regionId=" + region + "&size=1&page=1");
        assertThat(first.path("totalElements").asInt()).isEqualTo(2);
        assertThat(first.path("content").get(0).path("id"))
                .isNotEqualTo(second.path("content").get(0).path("id"));
        mvc.perform(get("/api/managers")
                        .param("regionId", region.toString())
                        .param("size", "101")
                        .cookie(cookie(lead)))
                .andExpect(status().isUnprocessableEntity());
        for (String path : List.of("/api/regions", "/api/data-sources", "/api/contract-types", "/api/file-categories"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        mvc.perform(get("/api/regions").cookie(cookie(manager))).andExpect(status().isForbidden());
    }

    private JsonNode read(UUID actor, String path) throws Exception {
        return response(mvc.perform(get(path).cookie(cookie(actor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
    }
}
