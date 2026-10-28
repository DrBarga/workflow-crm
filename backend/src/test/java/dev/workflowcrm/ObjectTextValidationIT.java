package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectTextValidationIT extends ObjectApiTestSupport {
    @Test
    void rejectsMarkupAndControlCharactersBeforeStorageWithFieldPaths() throws Exception {
        for (String text : List.of("<script>alert(1)</script>", "Hall" + (char) 0 + "Room")) {
            var body = body();
            body.put("name", text);
            create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("name"));
            body = body();
            body.put("contact", Map.of("name", "Owner", "role", text, "phone", "+380671234567"));
            create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("contact.role"));
        }
        assertThat(count("objects")).isZero();
    }
}
