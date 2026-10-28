package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.files.api.ObjectFileStorage;
import dev.workflowcrm.objects.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcObjectDeletionStore implements ObjectDeletionStore {
    private final JdbcTemplate jdbc;

    JdbcObjectDeletionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockObject(UUID id) {
        if (jdbc.query("select id from objects where id=? for update", (rs, n) -> rs.getObject(1, UUID.class), id)
                .isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
    }

    public Optional<ObjectDeletion> pending(UUID id) {
        return jdbc
                .query("select * from object_deletion_operations where object_id=? and state='PENDING'", this::map, id)
                .stream()
                .findFirst();
    }

    public ObjectDeletion create(UUID object, UUID actor, List<ObjectFileStorage.File> files) {
        jdbc.update("update objects set version=version+1,updated_at=clock_timestamp() where id=?", object);
        var operation = jdbc.queryForObject(
                "insert into object_deletion_operations(object_id,actor_id,actor_name) "
                        + "select ?,id,full_name from users where id=? returning *",
                this::map,
                object,
                actor);
        for (var file : files)
            jdbc.update(
                    "insert into object_deletion_files(operation_id,file_id,bucket,storage_key) values (?,?,?,?)",
                    operation.id(),
                    file.id(),
                    file.bucket(),
                    file.key());
        return operation;
    }

    public ObjectDeletion get(UUID id) {
        return jdbc.query("select * from object_deletion_operations where id=?", this::map, id).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deletion operation not found"));
    }

    public Optional<ObjectDeletion> claimNext() {
        return jdbc
                .query(
                        "select * from object_deletion_operations where state='PENDING' and next_attempt_at<=clock_timestamp() "
                                + "order by next_attempt_at,id limit 1 for update skip locked",
                        this::map)
                .stream()
                .findFirst();
    }

    public List<ObjectFileStorage.File> files(UUID id) {
        return jdbc.query(
                "select file_id,bucket,storage_key from object_deletion_files where operation_id=? order by file_id",
                (rs, n) -> new ObjectFileStorage.File(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                id);
    }

    public void complete(ObjectDeletion operation) {
        jdbc.query(
                "select set_config('workflow.object_deletion_actor',?,true), set_config('workflow.object_deletion_operation',?,true)",
                rs -> {},
                operation.actorId().toString(),
                operation.id().toString());
        jdbc.update("update object_deletion_files set purged=true where operation_id=?", operation.id());
        jdbc.update("delete from objects where id=?", operation.objectId());
        jdbc.update(
                "update object_deletion_operations set state='COMPLETED',completed_at=clock_timestamp(),attempts=attempts+1,last_error=null where id=?",
                operation.id());
    }

    public void failed(UUID id, String error) {
        jdbc.update(
                "update object_deletion_operations set attempts=attempts+1,last_error=?,next_attempt_at=clock_timestamp()+"
                        + "least(3600,power(2,least(attempts+1,12))) * interval '1 second' where id=? and state='PENDING'",
                error,
                id);
    }

    private ObjectDeletion map(ResultSet rs, int n) throws SQLException {
        var completed = rs.getTimestamp("completed_at");
        return new ObjectDeletion(
                rs.getObject("id", UUID.class),
                rs.getObject("object_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("state"),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
