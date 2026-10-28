package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectDuplicateAccessIT extends ObjectDuplicateTestSupport {
    @Test
    void refusedObjectsAreIncludedAndMarkedForTheirManager() throws Exception {
        existing(10, "Відхилений об’єкт", "Приватна адреса", manager, region, "REFUSED");
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.items[0].name").value("Відхилений об’єкт"))
                .andExpect(jsonPath("$.duplicates.items[0].address").value("Приватна адреса"))
                .andExpect(jsonPath("$.duplicates.items[0].status").value("REFUSED"))
                .andExpect(jsonPath("$.duplicates.items[0].refused").value(true));
    }

    @Test
    void hidesAllDetailsOfForeignRegionAndAnotherManagersObjectInOwnRegion() throws Exception {
        var ownRegion = existing(10, "Чужий об’єкт", "Прихована адреса", colleague, region, "REFUSED");
        var foreign = existing(20, "Чужий регіон", "Таємна адреса", outsider, otherRegion, "FOUND");
        var response = create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.total").value(2))
                .andReturn()
                .getResponse();
        var items = json.readTree(response.getContentAsByteArray())
                .path("duplicates")
                .path("items");
        for (var item : items) {
            assertThat(item.size()).isEqualTo(3);
            assertThat(item.path("restricted").asBoolean()).isTrue();
            assertThat(item.path("name").asText()).isNotBlank();
            assertThat(item.path("distanceMetres").asDouble()).isPositive();
        }
        assertThat(response.getContentAsString())
                .doesNotContain(
                        ownRegion.toString(),
                        foreign.toString(),
                        colleague.toString(),
                        outsider.toString(),
                        "Прихована",
                        "Таємна",
                        "REFUSED");
    }

    @Test
    void regionalLeadSeesOwnRegionButForeignRegionRemainsRestricted() throws Exception {
        existing(10, "Свій регіон", "Адреса", colleague, region, "REFUSED");
        existing(20, "Чужий регіон", "Таємна", outsider, otherRegion, "REFUSED");
        var body = body();
        body.put("managerId", manager.toString());
        create(lead, key(), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.items[0].restricted").value(false))
                .andExpect(jsonPath("$.duplicates.items[0].status").value("REFUSED"))
                .andExpect(jsonPath("$.duplicates.items[1].restricted").value(true))
                .andExpect(jsonPath("$.duplicates.items[1].status").doesNotExist());
    }
}
