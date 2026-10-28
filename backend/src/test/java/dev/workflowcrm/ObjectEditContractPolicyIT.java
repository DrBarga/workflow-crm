package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditContractPolicyIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "SUPERADMIN"})
    void manualTasksAndGeneralTemplateSetsDoNotLockContractType(String role) throws Exception {
        UUID object = add("General template", manager, region);
        tasks(object);
        ObjectContractTaskSetTestData.create(jdbc, object, null, null, "ACTIVE");
        assertThat(card(actor(role), object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isTrue();
        patch(actor(role), object, change(object, "contractTypeId", type())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select contract_type_id from objects where id=?", UUID.class, object))
                .isEqualTo(type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "COMPLETED", "CANCELLED"})
    void historicalContractTemplateSetLocksTypeInEveryState(String state) throws Exception {
        UUID object = add("Contract template", manager, region);
        UUID set = ObjectContractTaskSetTestData.create(jdbc, object, type(), null, state);
        jdbc.update(
                "update task_templates set contract_type_id=null where id=(select v.template_id from task_template_versions v join task_sets s on s.template_version_id=v.id where s.id=?)",
                set);
        for (UUID actor : List.of(manager, lead, director, contract)) {
            assertThat(card(actor, object)
                            .path("editableFields")
                            .path("contractTypeId")
                            .asBoolean())
                    .isFalse();
            patch(actor, object, change(object, "contractTypeId", type())).andExpect(status().isUnprocessableEntity());
        }
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
        patch(admin, object, change(object, "contractTypeId", type())).andExpect(status().isOk());
    }

    @Test
    void legacyContractTemplateVersionAlsoLocksType() throws Exception {
        UUID object = add("Legacy contract", manager, region);
        ObjectContractTaskSetTestData.create(jdbc, object, null, "Legacy contract", "CANCELLED");
        patch(manager, object, change(object, "contractTypeId", type())).andExpect(status().isUnprocessableEntity());
        assertThat(card(manager, object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void changingLiveTemplateDoesNotReclassifyExistingGeneralSet() throws Exception {
        UUID object = add("General snapshot", manager, region);
        UUID set = ObjectContractTaskSetTestData.create(jdbc, object, null, null, "COMPLETED");
        jdbc.update(
                "update task_templates set contract_type_id=? where id=(select v.template_id from task_template_versions v join task_sets s on s.template_version_id=v.id where s.id=?)",
                type(),
                set);
        patch(lead, object, change(object, "contractTypeId", type())).andExpect(status().isOk());
    }

    @Test
    void contractTypePermissionsStillRespectObjectScope() throws Exception {
        UUID object = add("Foreign object", outsider, otherRegion);
        patch(manager, object, change(object, "contractTypeId", type())).andExpect(status().isForbidden());
        patch(lead, object, change(object, "contractTypeId", type())).andExpect(status().isForbidden());
        patch(contract, object, change(object, "contractTypeId", type())).andExpect(status().isOk());
    }
}
