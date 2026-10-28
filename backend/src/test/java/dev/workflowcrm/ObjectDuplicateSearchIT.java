package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectDuplicateSearchIT extends ObjectDuplicateTestSupport {
    @Test
    void findsWithinDefaultRadiusAndExcludesOutsideWithoutTextMatch() throws Exception {
        var inside = nearby(49);
        nearby(51);
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATES_FOUND"))
                .andExpect(jsonPath("$.duplicates.total").value(1))
                .andExpect(jsonPath("$.duplicates.items[0].id").value(inside.toString()))
                .andExpect(
                        jsonPath("$.duplicates.items[0].distanceMetres").value(org.hamcrest.Matchers.closeTo(49, 0.02)))
                .andExpect(jsonPath("$.duplicates.items[0].status").value("FOUND"));
        assertThat(count("objects")).isEqualTo(2);
        assertThat(count("object_creation_requests")).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("object_duplicate_decisions")).isZero();
    }

    @Test
    void addressAndSimilarNameFindIndependentFarAwayMatchesWithoutDoubleCounting() throws Exception {
        var address = existing(1000, "Будівля", "  КИЇВ, НОВА ВУЛИЦЯ, 10. ", manager, region, "FOUND");
        existing(2000, "Новий торговельний об’єкт центр", "Інша вулиця 18", manager, region, "FOUND");
        existing(20, "Новий торговельний об’єкт", "Київ, Нова вулиця, 10", manager, region, "FOUND");
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.total").value(3))
                .andExpect(jsonPath("$.duplicates.items.length()").value(3))
                .andExpect(jsonPath("$.duplicates.items[1].id").value(address.toString()));
    }

    @Test
    void returnsTwentyNearestAndTotalIncludingTextOnlyMatches() throws Exception {
        for (int i = 30; i >= 1; i--) nearby(i);
        existing(5000, "Далеко", (String) body().get("address"), manager, region, "FOUND");
        var response = create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.total").value(31))
                .andExpect(jsonPath("$.duplicates.items.length()").value(20))
                .andReturn()
                .getResponse();
        var items = json.readTree(response.getContentAsByteArray())
                .path("duplicates")
                .path("items");
        for (int i = 0; i < 20; i++)
            assertThat(items.get(i).path("distanceMetres").asDouble())
                    .isCloseTo(i + 1, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void noMatchesCreateNormally() throws Exception {
        nearby(1000);
        create(manager, key(), body()).andExpect(status().isCreated());
    }

    @Test
    void generatedSearchColumnsTrackAddressAndNameUpdates() throws Exception {
        var existing = nearby(1000);
        jdbc.update(
                "update objects set name=?, address=? where id=?", body().get("name"), body().get("address"), existing);
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.total").value(1));
    }
}
