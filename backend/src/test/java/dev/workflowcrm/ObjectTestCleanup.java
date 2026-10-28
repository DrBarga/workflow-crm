package dev.workflowcrm;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class ObjectTestCleanup {
    private ObjectTestCleanup() {}

    static void reset(JdbcTemplate jdbc) {
        erase(jdbc, () -> resetTables(jdbc));
    }

    static void erase(JdbcTemplate jdbc, Runnable action) {
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transactions.executeWithoutResult(status -> {
            jdbc.execute("lock table objects in access exclusive mode");
            for (String table :
                    List.of("objects", "object_history", "object_deletion_audit", "object_deletion_operations"))
                jdbc.execute("alter table " + table + " disable trigger user");
            action.run();
            for (String table :
                    List.of("objects", "object_history", "object_deletion_audit", "object_deletion_operations"))
                jdbc.execute("alter table " + table + " enable trigger user");
        });
    }

    private static void resetTables(JdbcTemplate jdbc) {
        for (String table : List.of(
                "object_task_set_keys",
                "object_task_set_jobs",
                "object_import_rows",
                "object_imports",
                "object_import_audit",
                "object_exports",
                "object_export_audit",
                "file_upload_reservations",
                "object_deletion_operations",
                "files",
                "task_comments",
                "object_comments",
                "entry_point_approval_requests",
                "status_approval_requests",
                "user_notifications",
                "event_outbox",
                "object_status_audit",
                "task_history",
                "tasks",
                "task_sets",
                "task_template_items",
                "task_template_versions",
                "task_templates",
                "object_duplicate_decisions",
                "object_history",
                "object_creation_requests",
                "object_contacts",
                "contact_phone_views",
                "contact_history",
                "contacts",
                "objects",
                "object_deletion_audit",
                "users",
                "regions")) jdbc.update("delete from " + table);
    }
}
