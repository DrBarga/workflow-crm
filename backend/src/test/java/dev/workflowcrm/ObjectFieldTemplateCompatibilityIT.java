package dev.workflowcrm;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectFieldTemplateCompatibilityIT extends IcDecisionTestSupport {
    @Test
    void retainsLegacyTemplateMatchAfterContractDictionaryBackfill() throws Exception {
        historicalContract();
        var template = template();
        jdbc.update("update task_templates set contract_type='Основний' where id=?", template);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.tasks.length()").value(1));
    }

    @Test
    void rejectsDuplicateLogicalPairAfterContractDictionaryBackfill() throws Exception {
        historicalContract();
        var legacy = template();
        jdbc.update("update task_templates set contract_type='Основний' where id=?", legacy);
        jdbc.update("update task_template_items set name='Legacy terms' where template_id=?", legacy);
        var canonical = template();
        jdbc.update("update task_templates set contract_type='PRIMARY' where id=?", canonical);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TEMPLATE_CONFIGURATION_ERROR"));
    }

    private void historicalContract() {
        jdbc.update(
                "update objects set contract_type='Основний',contract_type_id=(select id from contract_types where code='PRIMARY') where id=?",
                object);
    }
}
