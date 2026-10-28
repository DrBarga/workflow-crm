package dev.workflowcrm.objects.infrastructure;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.audit.api.ObjectExportAudit;
import dev.workflowcrm.objects.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcObjectExportStore implements ObjectExportStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectExportAudit audit;

    JdbcObjectExportStore(JdbcTemplate jdbc, ObjectMapper json, ObjectExportAudit audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    public void enqueue(ObjectExport.Job job) {
        jdbc.execute("select pg_advisory_xact_lock(5784)");
        int global = jdbc.queryForObject(
                "select count(*) from object_exports where state='QUEUED' and expires_at>clock_timestamp()",
                Integer.class);
        int own = jdbc.queryForObject(
                "select count(*) from object_exports where actor_id=? and state='QUEUED' and expires_at>clock_timestamp()",
                Integer.class,
                job.actor());
        if (global >= 100 || own >= 2)
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Export queue is full; try again later");
        jdbc.update(
                """
                insert into object_exports(id,actor_id,format,filters,scope,requested_at,expires_at,bucket,storage_key)
                values(?,?,?,?::jsonb,?::jsonb,?,?,?,?)
                """,
                job.id(),
                job.actor(),
                job.format().name(),
                encode(job.filter()),
                encode(job.scope()),
                Timestamp.from(job.requestedAt()),
                Timestamp.from(job.expiresAt()),
                job.bucket(),
                job.key());
        audit.record(
                job.id(),
                job.actor(),
                "EXPORT_REQUESTED",
                Map.of("format", job.format(), "filters", job.filter(), "scope", job.scope()));
    }

    public ObjectExport.Job get(UUID id, UUID actor) {
        return jdbc.query("select * from object_exports where id=? and actor_id=?", this::read, id, actor).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Export not found"));
    }

    public Optional<ObjectExport.Job> claim(Instant now) {
        return jdbc
                .query(
                        """
                select * from object_exports where state='QUEUED' and next_attempt_at<=? and expires_at>?
                order by requested_at,id limit 1 for update skip locked
                """,
                        this::read,
                        Timestamp.from(now),
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    public Optional<ObjectExport.Job> expired(Instant now) {
        return jdbc
                .query(
                        "select * from object_exports where state<>'EXPIRED' and expires_at<=? order by expires_at,id limit 1 for update skip locked",
                        this::read,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    public void ready(UUID id, long rows, long bytes) {
        var job = jdbc.query("select * from object_exports where id=?", this::read, id)
                .getFirst();
        jdbc.update(
                "update object_exports set state='READY',row_count=?,size_bytes=?,completed_at=clock_timestamp(),error=null where id=?",
                rows,
                bytes,
                id);
        audit.record(
                id,
                job.actor(),
                "EXPORT_COMPLETED",
                Map.of(
                        "format",
                        job.format(),
                        "rows",
                        rows,
                        "bytes",
                        bytes,
                        "filters",
                        job.filter(),
                        "scope",
                        job.scope()));
    }

    public void failed(UUID id, String error, boolean retry) {
        jdbc.update(
                """
                update object_exports set attempts=attempts+1,error=?,
                state=case when ? and attempts<2 then 'QUEUED' else 'FAILED' end,
                next_attempt_at=clock_timestamp()+interval '10 seconds'*(attempts+1)
                where id=? and state='QUEUED'
                """,
                error,
                retry,
                id);
    }

    public void expire(UUID id) {
        jdbc.update("update object_exports set state='EXPIRED' where id=?", id);
    }

    private ObjectExport.Job read(ResultSet r, int n) throws SQLException {
        try {
            return new ObjectExport.Job(
                    r.getObject("id", UUID.class),
                    r.getObject("actor_id", UUID.class),
                    ObjectExport.Format.valueOf(r.getString("format")),
                    json.readValue(r.getString("filters"), ObjectListFilter.class),
                    json.readValue(r.getString("scope"), ObjectListScope.class),
                    r.getTimestamp("requested_at").toInstant(),
                    r.getTimestamp("expires_at").toInstant(),
                    r.getString("bucket"),
                    r.getString("storage_key"),
                    ObjectExport.State.valueOf(r.getString("state")),
                    r.getLong("row_count"),
                    r.getString("error"),
                    r.getInt("attempts"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read export job", e);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode export job", e);
        }
    }
}
