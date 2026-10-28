package dev.workflowcrm;

import liquibase.*;

final class ContactMigrationCheckpoint {
    private static final String LAST_CHANGE = "20261019_contact_audit_values";

    private ContactMigrationCheckpoint() {}

    static void update(Liquibase migration) throws Exception {
        migration.getDatabase().getConnection().commit();
        var pending = migration.listUnrunChangeSets(new Contexts(), new LabelExpression());
        for (int index = 0; index < pending.size(); index++) {
            if (LAST_CHANGE.equals(pending.get(index).getId())) {
                migration.update(index + 1, new Contexts(), new LabelExpression());
                return;
            }
        }
        if (migration.getDatabaseChangeLog().getChangeSets().stream()
                .noneMatch(change -> LAST_CHANGE.equals(change.getId())))
            throw new IllegalStateException("Contact audit migration checkpoint is missing");
    }
}
