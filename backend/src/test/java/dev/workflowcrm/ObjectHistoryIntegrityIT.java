package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectHistoryIntegrityIT extends ObjectHistoryTestSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void directUpdateDeleteAndTruncateAreRejected() {
        UUID object = add("Незмінна історія", manager, region);
        entry(object, manager, "name", "1", "2");
        for (String sql : List.of(
                "update object_history set new_value='tampered'",
                "delete from object_history",
                "truncate object_history"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void deletingObjectRequiresActiveAdministratorContext() {
        UUID object = add("Захищене видалення", manager, region);
        assertThatThrownBy(() -> jdbc.update("delete from objects where id=?", object))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> deleteAs(manager, object, false)).isInstanceOf(DataAccessException.class);
        jdbc.update("update users set status='DISABLED' where id=?", admin);
        assertThatThrownBy(() -> deleteAs(admin, object, false)).isInstanceOf(DataAccessException.class);
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_deletion_audit")).isZero();
    }

    @Test
    void administratorDeletionCascadesHistoryButPreservesImmutableSnapshot() throws Exception {
        UUID object = add("Архівний об’єкт", manager, region);
        entry(object, null, "STATUS", "FOUND", "IN_PROGRESS");
        deleteAs(admin, object, false);
        historyRequest(admin, object, Map.of()).andExpect(status().isNotFound());
        assertThat(count("object_history")).isZero();
        var audit = jdbc.queryForMap("select * from object_deletion_audit where object_id=?", object);
        assertThat(audit.get("actor_id")).isEqualTo(admin);
        assertThat(json.readTree(audit.get("snapshot").toString())
                        .path("object")
                        .path("name")
                        .asText())
                .isEqualTo("Архівний об’єкт");
        for (String sql : List.of(
                "delete from object_deletion_audit",
                "update object_deletion_audit set snapshot='{}'",
                "truncate object_deletion_audit"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
        assertThat(count("object_deletion_audit")).isEqualTo(1);
    }

    @Test
    void rolledBackDeletionPreservesObjectAndHistoryAndDoesNotLeakAdminContext() {
        UUID object = add("Відкат", manager, region);
        entry(object, manager, "name", "1", "2");
        deleteAs(admin, object, true);
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(count("object_deletion_audit")).isZero();
        assertThatThrownBy(() -> jdbc.update("delete from objects where id=?", object))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void historyFailureRollsBackApprovalCreation() throws Exception {
        UUID object = add("Атомарний запит", manager, region);
        state(object, "IC_PREPARATION");
        jdbc.execute(
                "alter table object_history add constraint test_approval_history_failure check(field_name<>'ENTRY_POINT_APPROVAL_REQUESTED')");
        try {
            point(manager, object, coordinates()).andExpect(status().isInternalServerError());
            assertThat(count("entry_point_approval_requests")).isZero();
            assertThat(count("object_history")).isZero();
            assertThat(count("user_notifications")).isZero();
        } finally {
            jdbc.execute("alter table object_history drop constraint test_approval_history_failure");
        }
    }

    private void deleteAs(UUID actor, UUID object, boolean rollback) {
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            jdbc.queryForObject(
                    "select set_config('workflow.object_deletion_actor',?,true)", String.class, actor.toString());
            jdbc.update("delete from objects where id=?", object);
            if (rollback) s.setRollbackOnly();
        });
    }
}
