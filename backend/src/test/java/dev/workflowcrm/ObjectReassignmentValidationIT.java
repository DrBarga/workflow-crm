package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectReassignmentValidationIT extends ObjectReassignmentTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void targetMustHaveManagerRole(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        reassign(lead, object, actor(role)).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
    }

    @Test
    void disabledMissingAndRegionlessManagersAreRejected() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        reassign(lead, object, colleague).andExpect(status().isUnprocessableEntity());
        reassign(lead, object, UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        jdbc.update("update users set status='ACTIVE' where id=?", colleague);
        UserRegionTestData.replace(jdbc, colleague);
        reassign(lead, object, colleague).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_history")).isZero();
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void disabledPreviousManagerDoesNotPreventTransfer() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "IN_PROGRESS");
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        reassign(lead, object, colleague).andExpect(status().isOk());
        assertThat(assignee(task)).isEqualTo(colleague);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "\"0\"", "null", "9223372036854775808"})
    void invalidVersionsAreRejected(String value) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var body = new HashMap<String, Object>();
        body.put("managerId", colleague);
        body.put("version", json.readTree(value));
        reassign(lead, object, body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void requiredFieldsAndUnknownFieldsAreValidated() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        reassign(lead, object, Map.of("managerId", colleague)).andExpect(status().isUnprocessableEntity());
        reassign(lead, object, Map.of("version", 0)).andExpect(status().isUnprocessableEntity());
        for (String value : List.of("", "1-1-1-1-1", "not-a-uuid"))
            reassign(lead, object, Map.of("version", 0, "managerId", value))
                    .andExpect(status().isUnprocessableEntity());
        reassign(lead, object, Map.of("version", 0, "managerId", colleague, "regionId", region))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void currentManagerIsNoOpAndOldVersionIsConflict() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        assertThat(response(reassign(lead, object, manager).andExpect(status().isOk()))
                        .path("saved")
                        .asBoolean())
                .isFalse();
        assertThat(version(object)).isZero();
        reassign(lead, object, colleague).andExpect(status().isOk());
        var error = response(reassign(lead, object, Map.of("version", 0, "managerId", colleague))
                .andExpect(status().isConflict()));
        assertThat(error.path("currentVersion").asLong()).isEqualTo(1);
        reassign(lead, object, colleague).andExpect(status().isOk());
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(count("user_notifications")).isEqualTo(2);
    }
}
