package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditDataSourceIT extends ObjectEditTestSupport {
    UUID source() {
        return jdbc.queryForObject("select id from object_data_sources where code='BROKER'", UUID.class);
    }

    UUID storedSource(UUID object) {
        return jdbc.queryForObject("select data_source_id from objects where id=?", UUID.class, object);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void generalEditorsAssignAndClearSourceWithHistoryAndVersion(String role) throws Exception {
        UUID object = add("Джерело", manager, region);
        UUID source = source();
        patch(actor(role), object, change(object, "dataSourceId", source)).andExpect(status().isOk());
        assertThat(storedSource(object)).isEqualTo(source);
        assertThat(card(manager, object).path("dataSourceId").asText()).isEqualTo(source.toString());
        assertThat(card(actor(role), object)
                        .path("editableFields")
                        .path("dataSourceId")
                        .asBoolean())
                .isTrue();
        assertThat(version(object)).isEqualTo(1);
        var history = jdbc.queryForMap(
                "select previous_value,new_value from object_history where object_id=? and field_name='dataSourceId'",
                object);
        assertThat(history.get("previous_value")).isNull();
        assertThat(history.get("new_value")).isEqualTo(json.writeValueAsString(source));
        patch(actor(role), object, change(object, "dataSourceId", null)).andExpect(status().isOk());
        assertThat(storedSource(object)).isNull();
        assertThat(version(object)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name='dataSourceId'",
                        Integer.class,
                        object))
                .isEqualTo(2);
        assertThat(count("object_status_audit")).isEqualTo(2);
        assertThat(count("event_outbox")).isEqualTo(2);
    }

    @Test
    void omittedAndUnchangedSourcePreserveHistoricalInactiveReference() throws Exception {
        UUID object = add("Історичне джерело", manager, region);
        UUID source = source();
        patch(manager, object, change(object, "dataSourceId", source)).andExpect(status().isOk());
        var same = response(
                patch(manager, object, change(object, "dataSourceId", source)).andExpect(status().isOk()));
        assertThat(same.path("saved").asBoolean()).isFalse();
        assertThat(version(object)).isEqualTo(1);
        jdbc.update("update object_data_sources set is_active=false where id=?", source);
        try {
            patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isOk());
            assertThat(storedSource(object)).isEqualTo(source);
            patch(manager, object, change(object, "dataSourceId", source))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("dataSourceId"));
            patch(manager, object, change(object, "dataSourceId", null)).andExpect(status().isOk());
            assertThat(storedSource(object)).isNull();
        } finally {
            jdbc.update("update object_data_sources set is_active=true where id=?", source);
        }
    }

    @Test
    void invalidSourceRejectsEntirePatchAndStaleVersionCannotOverwriteIt() throws Exception {
        UUID object = add("Перевірка джерела", manager, region);
        for (Object invalid : List.of(UUID.randomUUID(), "bad-id")) {
            var body = change(object, "dataSourceId", invalid);
            body.put("name", "Не зберігати");
            patch(manager, object, body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("dataSourceId"));
        }
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
        patch(manager, object, change(object, "dataSourceId", source())).andExpect(status().isOk());
        patch(lead, object, new LinkedHashMap<>(Map.of("version", 0, "name", "Застаріла зміна")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(storedSource(object)).isEqualTo(source());
        assertThat(card(manager, object).path("name").asText()).isEqualTo("Перевірка джерела");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEO", "CONTRACT_MANAGER"})
    void readOnlyAndContractRolesCannotEditSource(String role) throws Exception {
        UUID object = add("Права джерела", manager, region);
        patch(actor(role), object, change(object, "dataSourceId", source())).andExpect(status().isForbidden());
        assertThat(card(actor(role), object)
                        .path("editableFields")
                        .path("dataSourceId")
                        .asBoolean())
                .isFalse();
        assertThat(version(object)).isZero();
    }

    @Test
    void sourceUsesTheSameObjectAndRegionScopeAsOtherFields() throws Exception {
        UUID object = add("Чужий об’єкт", outsider, otherRegion);
        for (UUID actor : List.of(manager, lead))
            patch(actor, object, change(object, "dataSourceId", source())).andExpect(status().isForbidden());
        assertThat(storedSource(object)).isNull();
        assertThat(count("object_history")).isZero();
    }
}
