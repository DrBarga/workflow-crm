package dev.workflowcrm;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {"app.objects.duplicates.radius-metres=120", "app.objects.duplicates.name-similarity=0.95"})
class ObjectDuplicateSettingsIT extends ObjectDuplicateTestSupport {
    @Test
    void configurableRadiusReplacesDefaultAndSimilarityThresholdIsApplied() throws Exception {
        var within = nearby(100);
        nearby(121);
        existing(1000, "Новий торговельний об’єкт центр", "Далека вулиця", manager, region, "FOUND");
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.total").value(1))
                .andExpect(jsonPath("$.duplicates.items[0].id").value(within.toString()));
    }
}
