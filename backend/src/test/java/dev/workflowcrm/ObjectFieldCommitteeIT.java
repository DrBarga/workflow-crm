package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectFieldCommitteeIT extends IcDecisionTestSupport {
    @Test
    void acceptsFiftyCharacterConditionsAndRejectsFiftyOneWithoutSaving() throws Exception {
        var body = decision("APPROVED_WITH_CONDITIONS");
        body.put("conditions", "а".repeat(51));
        decide(lead, body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("conditions"));
        assertThat(state()).isEqualTo("IC_PREPARATION");
        body.put("conditions", "а".repeat(50));
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions").value("а".repeat(50)));
    }

    @Test
    void recordsRefusalCommentInResponseObjectAndDecisionHistory() throws Exception {
        var body = decision("REFUSED");
        body.put("refusalComment", "  Не відповідає вимогам  ");
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusalComment").value("Не відповідає вимогам"));
        assertThat(jdbc.queryForObject(
                        "select investment_committee_refusal_comment from objects where id=?", String.class, object))
                .isEqualTo("Не відповідає вимогам");
        assertThat(jdbc.queryForObject(
                        "select new_value::jsonb->>'refusalComment' from object_history where field_name='IC_DECISION' and object_id=?",
                        String.class,
                        object))
                .isEqualTo("Не відповідає вимогам");
        assertThat(count("object_history")).isEqualTo(2);
    }

    @Test
    void rejectsOversizedAndIrrelevantRefusalComments() throws Exception {
        var body = decision("REFUSED");
        body.put("refusalComment", "а".repeat(51));
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        body = decision("APPROVED");
        body.put("refusalComment", "irrelevant");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_history")).isZero();
    }
}
