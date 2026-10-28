package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.audit.api.ObjectImportAudit;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.FieldViolation;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcObjectImportStore implements ObjectImportStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectImportAudit audit;
    private final EntityManager entities;

    JdbcObjectImportStore(JdbcTemplate jdbc, ObjectMapper json, ObjectImportAudit audit, EntityManager entities) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.entities = entities;
    }

    public ObjectImport.View enqueue(UUID actor, String hash, List<ObjectImport.Input> rows) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended('objects:import-upload',0))", r -> {});
        var previous = jdbc.query(
                "select id from object_imports where file_hash=?", (r, n) -> r.getObject(1, UUID.class), hash);
        if (!previous.isEmpty()) return view(previous.getFirst());
        if (jdbc.queryForObject("select count(*) from object_imports where state='QUEUED'", Integer.class) >= 10)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Import queue is full; retry later");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into object_imports(id,actor_id,file_hash,row_count) values(?,?,?,?)",
                id,
                actor,
                hash,
                rows.size());
        jdbc.batchUpdate(
                "insert into object_import_rows(import_id,row_number,input) values(?,?,?::jsonb)",
                rows,
                200,
                (s, row) -> {
                    s.setObject(1, id);
                    s.setInt(2, row.row());
                    s.setString(3, write(row.values()));
                });
        audit.record(id, actor, "IMPORT_REQUESTED", Map.of("rows", rows.size()));
        return view(id);
    }

    public Optional<ObjectImport.Job> claim() {
        var job = jdbc
                .query(
                        "select id,actor_id,file_hash from object_imports where state='QUEUED' and next_attempt_at<=current_timestamp order by requested_at,id for update skip locked limit 1",
                        (r, n) -> new ObjectImport.Job(
                                r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3)))
                .stream()
                .findFirst();
        if (job.isPresent()) {
            // Keep GIN pending entries bounded while checking duplicates between inserts.
            jdbc.execute("set local gin_pending_list_limit=64");
            // Replan searches as the batch grows instead of retaining an empty-table plan.
            jdbc.execute("set local plan_cache_mode=force_custom_plan");
        }
        return job;
    }

    public ObjectImport.View view(UUID id) {
        return jdbc
                .query(
                        "select * from object_imports where id=?",
                        (r, n) -> new ObjectImport.View(
                                id,
                                r.getObject("actor_id", UUID.class),
                                r.getString("state"),
                                r.getInt("row_count"),
                                r.getInt("created_count"),
                                r.getInt("rejected_count"),
                                r.getInt("duplicate_count"),
                                r.getTimestamp("requested_at").toInstant(),
                                r.getTimestamp("completed_at") == null
                                        ? null
                                        : r.getTimestamp("completed_at").toInstant(),
                                r.getString("error")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    public List<ObjectImport.Input> inputs(UUID id) {
        return jdbc.query(
                "select row_number,input from object_import_rows where import_id=? order by row_number",
                (r, n) -> new ObjectImport.Input(
                        r.getInt(1), read(r.getString(2), new TypeReference<Map<String, String>>() {})),
                id);
    }

    public ObjectImport.Report report(UUID id, int offset, int limit) {
        var rows = jdbc.query(
                "select * from object_import_rows where import_id=? order by row_number limit ? offset ?",
                this::row,
                id,
                limit,
                offset);
        return new ObjectImport.Report(rows, offset, limit, view(id).total());
    }

    private ObjectImport.Row row(ResultSet r, int n) throws SQLException {
        return new ObjectImport.Row(
                r.getInt("row_number"),
                r.getString("state"),
                r.getObject("object_id", UUID.class),
                r.getBoolean("possible_duplicate"),
                r.getString("note"),
                read(r.getString("errors"), new TypeReference<List<FieldViolation>>() {}));
    }

    public void row(UUID id, ObjectImport.Row row) {
        if (row.row() % 100 == 0) {
            entities.flush();
            entities.clear();
        }
        jdbc.update(
                "update object_import_rows set state=?,object_id=?,possible_duplicate=?,note=?,errors=?::jsonb,input=null where import_id=? and row_number=?",
                row.state(),
                row.objectId(),
                row.possibleDuplicate(),
                row.note(),
                write(row.errors()),
                id,
                row.row());
    }

    public void complete(UUID id) {
        jdbc.update(
                """
            update object_imports set state='COMPLETED',completed_at=current_timestamp,attempts=attempts+1,error=null,
            created_count=(select count(*) from object_import_rows where import_id=? and state='CREATED'),
            rejected_count=(select count(*) from object_import_rows where import_id=? and state='REJECTED'),
            duplicate_count=(select count(*) from object_import_rows where import_id=? and possible_duplicate) where id=?
            """,
                id,
                id,
                id,
                id);
        var v = view(id);
        audit.record(
                id,
                v.author(),
                "IMPORT_COMPLETED",
                Map.of("created", v.created(), "rejected", v.rejected(), "duplicates", v.duplicates()));
    }

    public void failed(UUID id, String reason, boolean retry) {
        jdbc.query("select id from object_imports where id=? for update", r -> {}, id);
        int changed = jdbc.update(
                "update object_imports set attempts=attempts+1,error=?,state=case when ? and attempts<2 then 'QUEUED' else 'FAILED' end,next_attempt_at=current_timestamp+interval '5 seconds' where id=? and state='QUEUED'",
                reason,
                retry,
                id);
        if (changed == 0) return;
        var v = view(id);
        if (v.state().equals("FAILED")) {
            var errors = write(
                    List.of(new FieldViolation("row", "Import was rolled back; no objects were created: " + reason)));
            jdbc.update(
                    "update object_import_rows set state='NOT_APPLIED',errors=?::jsonb,input=null where import_id=?",
                    errors,
                    id);
            jdbc.update("update object_imports set completed_at=current_timestamp where id=?", id);
        }
        audit.record(
                id,
                v.author(),
                "IMPORT_ROLLED_BACK",
                Map.of("reason", reason, "retry", v.state().equals("QUEUED")));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode import", e);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot decode import", e);
        }
    }
}
