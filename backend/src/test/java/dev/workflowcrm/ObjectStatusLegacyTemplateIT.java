package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusLegacyTemplateIT extends ObjectStatusTasksSupport {
    @Test
    void nonTypeTransitionPreservesHistoricalTypeEvenWhenDictionaryEntryIsInactive() throws Exception {
        var type = contractType();
        jdbc.update("update objects set contract_type_id=?,contract_type='Основний' where id=?", type, object);
        jdbc.update("update contract_types set is_active=false where id=?", type);
        try {
            change(manager, command("PAUSED")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select contract_type from objects where id=?", String.class, object))
                    .isEqualTo("Основний");
            assertThat(jdbc.queryForObject("select contract_type_id from objects where id=?", UUID.class, object))
                    .isEqualTo(type);
        } finally {
            jdbc.update("update contract_types set is_active=true where id=?", type);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIMARY", "PRELIMINARY"})
    void retainsLegacyFallbackOnlyWhenContractTypeIsUnchanged(String type) throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update(
                "update objects set contract_type_id=?,contract_type='Основний' where id=?", contractType(), object);
        template("CONTRACT_SIGNED", "Основний");
        var body = command("CONTRACT_SIGNED");
        body.put("contractSignedDate", "2026-10-04");
        body.put("contractTypeId", jdbc.queryForObject("select id from contract_types where code=?", UUID.class, type));
        var result = response(change(contract, body).andExpect(status().isOk()));
        if ("PRIMARY".equals(type)) {
            assertThat(result.path("saved").asBoolean()).isFalse();
            body.put("confirmationToken", result.path("confirmationToken").asText());
            change(contract, body).andExpect(status().isOk());
            assertThat(taskSnapshot()).hasSize(4);
        } else {
            assertThat(result.path("saved").asBoolean()).isTrue();
            assertThat(taskSnapshot()).hasSize(3);
            assertThat(jdbc.queryForObject("select contract_type from objects where id=?", String.class, object))
                    .isNull();
        }
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }
}
