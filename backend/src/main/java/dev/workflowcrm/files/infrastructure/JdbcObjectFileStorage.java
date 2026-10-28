package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.api.ObjectFileStorage;
import dev.workflowcrm.integrations.api.BinaryStorage;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectFileStorage implements ObjectFileStorage {
    private final JdbcTemplate jdbc;
    private final BinaryStorage storage;

    JdbcObjectFileStorage(JdbcTemplate jdbc, BinaryStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public List<File> files(UUID objectId) {
        return jdbc.query(
                "select id,bucket,storage_key from files where object_id=? or task_id in "
                        + "(select id from tasks where object_id=?) order by id",
                (rs, n) -> new File(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                objectId,
                objectId);
    }

    public void purge(File file) {
        storage.deleteAllVersions(file.bucket(), file.key());
    }
}
