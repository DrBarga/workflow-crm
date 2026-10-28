package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectCreationSourceIT extends ObjectApiTestSupport {
    @Test
    void savesOptionalSourceAndKeepsReplayStableAfterDictionaryValueIsDisabled() throws Exception {
        var source = jdbc.queryForObject("select id from object_data_sources where code='BROKER'", UUID.class);
        var body = body();
        body.put("dataSourceId", source.toString());
        String key = key();
        String response = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataSourceId").value(source.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(jdbc.queryForObject("select data_source_id from objects", UUID.class))
                .isEqualTo(source);
        jdbc.update("update object_data_sources set is_active=false where id=?", source);
        try {
            assertThat(create(manager, key, body)
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString())
                    .isEqualTo(response);
            create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("dataSourceId"));
        } finally {
            jdbc.update("update object_data_sources set is_active=true where id=?", source);
        }
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void omittedAndNullSourceHaveSameFingerprintAndDifferentSourceCannotReuseKey() throws Exception {
        var body = body();
        String key = key();
        create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataSourceId").isEmpty());
        body.put("dataSourceId", null);
        create(manager, key, body).andExpect(status().isCreated());
        body.put(
                "dataSourceId",
                jdbc.queryForObject("select id::text from object_data_sources where code='OTHER'", String.class));
        create(manager, key, body).andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownSourceAndInvalidUuidWithoutSavingObject() throws Exception {
        var body = body();
        for (var source : java.util.List.of(UUID.randomUUID().toString(), "invalid", 123, false)) {
            body.put("dataSourceId", source);
            create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("dataSourceId"));
        }
        assertThat(count("objects")).isZero();
        assertThat(count("object_creation_requests")).isZero();
    }
}
