package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCreationRegionsIT extends ObjectApiTestSupport {
    @Test
    void managerCreatesInAdditionalRegionOnlyForSelfAndReplayChecksCurrentMembership() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", manager, otherRegion);
        var body = body();
        body.put("regionId", otherRegion.toString());
        body.put("managerId", manager.toString());
        body.put("entrance", Map.of("longitude", 35.04, "latitude", 48.46));
        String key = key();
        String first = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionId").value(otherRegion.toString()))
                .andExpect(jsonPath("$.managerId").value(manager.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(create(manager, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .isEqualTo(first);
        assertThat(jdbc.queryForObject(
                        "select (select region_id from user_regions where user_id=? order by region_id limit 1)",
                        java.util.UUID.class,
                        manager))
                .isEqualTo(region);
        jdbc.update("delete from user_regions where user_id=?", manager);
        create(manager, key, body).andExpect(status().isForbidden());
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void managerWithOnlyAdditionalAssignmentCanCreateThereButNotForAnotherManager() throws Exception {
        UserRegionTestData.replace(jdbc, manager, otherRegion);
        var body = body();
        body.put("regionId", otherRegion.toString());
        body.put("managerId", outsider.toString());
        create(manager, key(), body).andExpect(status().isForbidden());
        body.remove("managerId");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerId").value(manager.toString()));
    }

    @Test
    void directorWithoutRegionAssignsManagerWhoseAdditionalRegionMatchesObject() throws Exception {
        var director = java.util.UUID.randomUUID();
        user(director, "DEVELOPMENT_DIRECTOR", null);
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", colleague, otherRegion);
        var body = body();
        body.put("regionId", otherRegion.toString());
        body.put("managerId", colleague.toString());
        create(director, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerId").value(colleague.toString()))
                .andExpect(jsonPath("$.authorId").value(director.toString()));
    }

    @Test
    void directorMustChooseActiveManagerAssignedToSelectedRegion() throws Exception {
        var director = java.util.UUID.randomUUID();
        user(director, "DEVELOPMENT_DIRECTOR", null);
        var body = body();
        create(director, key(), body).andExpect(status().isUnprocessableEntity());
        for (var target : java.util.List.of(outsider, director, java.util.UUID.randomUUID())) {
            body.put("managerId", target.toString());
            create(director, key(), body).andExpect(status().isForbidden());
        }
        body.put("managerId", colleague.toString());
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        create(director, key(), body).andExpect(status().isForbidden());
        assertThat(count("objects")).isZero();
    }
}
