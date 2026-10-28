package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCreationNameIT extends ObjectApiTestSupport {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\u2003\u2003", "\u00a0\u202f"})
    void fillsMissingEmptyOrWhitespaceNameFromAddress(String name) throws Exception {
        var body = body();
        if (name == null) body.remove("name");
        else body.put("name", name);
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Адреса вручну"));
        assertThat(jdbc.queryForObject("select name from objects", String.class))
                .isEqualTo("Адреса вручну");
    }

    @Test
    void preservesManualNameWithOnlyOuterWhitespaceRemoved() throws Exception {
        var body = body();
        body.put("name", "  Приміщення  біля парку  ");
        body.remove("address");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Приміщення  біля парку"));
        assertThat(jdbc.queryForObject("select name from objects", String.class))
                .isEqualTo("Приміщення  біля парку");
    }
}
