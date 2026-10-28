package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UserMultipleRegionsIT extends ObjectApiTestSupport {
    final UUID admin = UUID.randomUUID();

    @BeforeEach
    void administrator() {
        user(admin, "SUPERADMIN", null);
    }

    @Test
    void createsMultipleAssignmentsAndFiltersByAnyAssignedRegionWithoutDuplicates() throws Exception {
        var created = response(send(
                        post("/api/users"),
                        Map.of(
                                "email",
                                "multiple@example.com",
                                "fullName",
                                "Multiple Regions",
                                "role",
                                "REGIONAL_MANAGER",
                                "regionIds",
                                List.of(region, otherRegion, region)))
                .andExpect(status().isCreated()));
        UUID id = UUID.fromString(created.path("id").asText());
        assertThat(created.path("regionIds").size()).isEqualTo(2);
        assertThat(jdbc.queryForList("select region_id from user_regions where user_id=?", UUID.class, id))
                .containsExactlyInAnyOrder(region, otherRegion);
        var listed = response(mvc.perform(get("/api/users")
                        .cookie(cookie(admin))
                        .param("regionId", otherRegion.toString())
                        .param("role", "REGIONAL_MANAGER"))
                .andExpect(status().isOk()));
        assertThat(listed.path("content").size()).isEqualTo(1);
        assertThat(listed.path("content").get(0).path("id").asText()).isEqualTo(id.toString());
    }

    @Test
    void replacementIsVersionedAuditedAndNoopDoesNotWriteAgain() throws Exception {
        send(patch("/api/users/{id}", lead), Map.of("regionIds", List.of(region, otherRegion), "version", 0))
                .andExpect(status().isOk());
        var updated = response(
                mvc.perform(get("/api/users/{id}", lead).cookie(cookie(admin))).andExpect(status().isOk()));
        assertThat(updated.path("version").asLong()).isEqualTo(1);
        send(patch("/api/users/{id}", lead), Map.of("regionIds", List.of(otherRegion, region, region), "version", 1))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select version from users where id=?", Long.class, lead))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_access_history where user_id=? and actor_id=?",
                        Integer.class,
                        lead,
                        admin))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select cardinality(new_region_ids) from user_access_history where user_id=? and actor_id=?",
                        Integer.class,
                        lead,
                        admin))
                .isEqualTo(2);
        send(patch("/api/users/{id}", lead), Map.of("regionIds", List.of(), "version", 0))
                .andExpect(status().isConflict());
        send(patch("/api/users/{id}", lead), Map.of("regionIds", List.of(), "version", 1, "role", "CEO"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from user_regions where user_id=?", Integer.class, lead))
                .isZero();
    }

    @Test
    void invalidOrAmbiguousAssignmentsDoNotPartiallyReplaceExistingLinks() throws Exception {
        for (var body : List.of(
                Map.of("regionIds", List.of(region, UUID.randomUUID())),
                Map.of("regionId", region, "regionIds", List.of(otherRegion)),
                Map.of("regionIds", Arrays.asList(region, null)))) {
            send(patch("/api/users/{id}", lead), body).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForList("select region_id from user_regions where user_id=?", UUID.class, lead))
                .containsExactly(region);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_access_history where user_id=? and actor_id=?",
                        Integer.class,
                        lead,
                        admin))
                .isZero();
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return mvc.perform(request.cookie(cookie(admin), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    private com.fasterxml.jackson.databind.JsonNode response(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
