package dev.workflowcrm;

import java.sql.Connection;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;

final class ObjectMigrationBaseline {
    private ObjectMigrationBaseline() {}

    static void rollbackFrom(Liquibase migration, Connection connection, String changeSetId) throws Exception {
        rollbackFrom(migration, connection, changeSetId, "workflowcrm");
    }

    static void rollbackFrom(Liquibase migration, Connection connection, String changeSetId, String author)
            throws Exception {
        int count;
        try (var statement = connection.prepareStatement(
                "select count(*) from databasechangelog where orderexecuted >= (select orderexecuted from databasechangelog where id=? and author=?)")) {
            statement.setString(1, changeSetId);
            statement.setString(2, author);
            try (var result = statement.executeQuery()) {
                result.next();
                count = result.getInt(1);
            }
        }
        if (count == 0) {
            throw new SQLException("Baseline changeset was not applied: " + changeSetId);
        }
        migration.rollback(count, new Contexts(), new LabelExpression());
    }
}
