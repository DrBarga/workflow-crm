package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectDuplicateAtomicityIT extends ObjectDuplicateTestSupport {
    @Test
    void confirmationJournalFailureRollsBackObjectHistoryAndKey() throws Exception {
        nearby(10);
        jdbc.execute(
                "alter table object_duplicate_decisions add constraint test_audit_failure check (decision <> 'CREATED')");
        try {
            var body = body();
            body.put("confirmDuplicates", true);
            create(manager, key(), body).andExpect(status().isInternalServerError());
            assertThat(count("objects")).isEqualTo(1);
            assertThat(count("object_history")).isZero();
            assertThat(count("object_creation_requests")).isZero();
            assertThat(count("object_duplicate_decisions")).isZero();
        } finally {
            jdbc.execute("alter table object_duplicate_decisions drop constraint test_audit_failure");
        }
    }

    @Test
    void confirmationHistoryFailureAlsoRollsBackJournal() throws Exception {
        nearby(10);
        jdbc.execute(
                "alter table object_history add constraint test_confirmation_failure check (field_name <> 'DUPLICATES_CONFIRMED')");
        try {
            var body = body();
            body.put("confirmDuplicates", true);
            create(manager, key(), body).andExpect(status().isInternalServerError());
            assertThat(count("objects")).isEqualTo(1);
            assertThat(count("object_duplicate_decisions")).isZero();
            assertThat(count("object_creation_requests")).isZero();
        } finally {
            jdbc.execute("alter table object_history drop constraint test_confirmation_failure");
        }
    }
}
