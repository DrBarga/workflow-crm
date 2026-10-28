package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectCreationDuplicateRegionsIT extends ObjectDuplicateTestSupport {
    @Test
    void directorCanPreviewAndConfirmDuplicatesWithoutPersonalRegion() throws Exception {
        var director = UUID.randomUUID();
        user(director, "DEVELOPMENT_DIRECTOR", null);
        var existing = existing(10, "Знайдений об’єкт", "Адреса", outsider, otherRegion, "FOUND");
        var body = body();
        body.put("managerId", manager.toString());
        String key = key();
        create(director, key, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.items[0].id").value(existing.toString()))
                .andExpect(jsonPath("$.duplicates.items[0].restricted").value(false));
        body.put("confirmDuplicates", true);
        create(director, key, body).andExpect(status().isCreated());
        assertThat(count("object_duplicate_decisions")).isEqualTo(1);
    }

    @Test
    void repeatedDirectorCancellationResolvesRegionFromBoundary() throws Exception {
        var director = UUID.randomUUID();
        user(director, "DEVELOPMENT_DIRECTOR", null);
        ObjectRegionBoundaryFixture.load(jdbc, region);
        nearby(10);
        var body = body();
        body.remove("regionId");
        body.put("managerId", manager.toString());
        String key = key();
        cancel(director, key, body).andExpect(status().isNoContent());
        cancel(director, key, body).andExpect(status().isNoContent());
        assertThat(count("object_duplicate_decisions")).isEqualTo(1);
    }

    @Test
    void managerSeesOwnedDuplicateInAdditionalRegion() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", manager, otherRegion);
        var existing = existing(10, "Свій об’єкт", "Адреса", manager, otherRegion, "FOUND");
        var body = body();
        body.put("regionId", otherRegion.toString());
        create(manager, key(), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.items[0].id").value(existing.toString()))
                .andExpect(jsonPath("$.duplicates.items[0].restricted").value(false));
    }
}
