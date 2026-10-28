package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcObjectTaskSetQueue implements ObjectTaskSetQueue {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcObjectTaskSetQueue(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void lockKey(UUID actor, String key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,5808))", actor + ":" + key);
    }

    public Optional<Submission> submission(UUID actor, String key) {
        return jdbc
                .query(
                        """
                select j.*,k.fingerprint from object_task_set_keys k
                join object_task_set_jobs j on j.id=k.job_id where k.actor_id=? and k.idempotency_key=?
                """,
                        (r, n) -> new Submission(row(r), r.getString("fingerprint")),
                        actor,
                        key)
                .stream()
                .findFirst();
    }

    public Optional<Submission> confirmation(UUID actor, UUID object, String token) {
        return jdbc
                .query(
                        """
                select j.*,k.fingerprint from object_task_set_jobs j
                join object_task_set_keys k on k.job_id=j.id
                where j.actor_id=? and j.object_id=? and j.confirmation_token=? and j.state<>'FAILED' limit 1
                """,
                        (r, n) -> new Submission(row(r), r.getString("fingerprint")),
                        actor,
                        object,
                        token)
                .stream()
                .findFirst();
    }

    public boolean pending(UUID object) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from object_task_set_jobs where object_id=? and state='QUEUED')",
                Boolean.class,
                object));
    }

    public ObjectTaskSetJob enqueue(UUID actor, UUID object, String token, ObjectTaskSetCommand command) {
        return jdbc.queryForObject(
                """
                insert into object_task_set_jobs(actor_id,object_id,confirmation_token,command)
                values(?,?,?,?::jsonb) returning *
                """,
                (r, n) -> row(r),
                actor,
                object,
                token,
                encode(command));
    }

    public void remember(UUID actor, String key, String fingerprint, UUID job) {
        jdbc.update(
                "insert into object_task_set_keys(actor_id,idempotency_key,fingerprint,job_id) values(?,?,?,?)",
                actor,
                key,
                fingerprint,
                job);
    }

    public ObjectTaskSetJob get(UUID id, UUID actor, UUID object) {
        return jdbc
                .query(
                        "select * from object_task_set_jobs where id=? and actor_id=? and object_id=?",
                        (r, n) -> row(r),
                        id,
                        actor,
                        object)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task-set job not found"));
    }

    public Optional<Work> claim() {
        return jdbc
                .query(
                        """
                select * from object_task_set_jobs where state='QUEUED' and available_at<=current_timestamp
                order by available_at,created_at,id limit 1 for update skip locked
                """,
                        (r, n) -> new Work(row(r), decode(r.getString("command"))))
                .stream()
                .findFirst();
    }

    public Optional<Work> claim(UUID id) {
        return jdbc
                .query(
                        "select * from object_task_set_jobs where id=? and state='QUEUED' and available_at<=current_timestamp for update",
                        (r, n) -> new Work(row(r), decode(r.getString("command"))),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<UUID> delivery() {
        return jdbc
                .queryForList(
                        """
                select id from object_task_set_jobs where state='QUEUED' and available_at<=current_timestamp
                and (dispatched_at is null or dispatched_at<current_timestamp-interval '30 seconds')
                order by available_at,created_at,id limit 1
                """,
                        UUID.class)
                .stream()
                .findFirst();
    }

    public void sent(UUID id) {
        jdbc.update(
                "update object_task_set_jobs set dispatched_at=clock_timestamp() where id=? and state='QUEUED'", id);
    }

    public void complete(Work work, ObjectStatusResult result) {
        jdbc.update(
                """
                update object_task_set_jobs set state=?,approval_request_id=?,
                    task_set_id=(select s.id from task_sets s join object_statuses os on os.id=s.trigger_status_id
                        where s.object_id=? and os.code=? and s.state='ACTIVE'),
                    attempts=attempts+1,error=null,updated_at=clock_timestamp() where id=?
                """,
                result.saved() ? "SUCCEEDED" : "AWAITING_APPROVAL",
                result.approvalRequestId(),
                work.job().objectId(),
                result.saved() ? result.status() : null,
                work.job().id());
    }

    public void failed(UUID id, String error, boolean retry) {
        jdbc.update(
                """
                update object_task_set_jobs set attempts=attempts+1,error=?,
                    state=case when ? and attempts<2 then 'QUEUED' else 'FAILED' end,
                    available_at=current_timestamp+interval '5 seconds',dispatched_at=null,updated_at=clock_timestamp()
                where id=? and state='QUEUED'
                """,
                error,
                retry,
                id);
    }

    public void approvalResolved(UUID request, String decision, ObjectStatusResult result) {
        jdbc.update(
                """
                update object_task_set_jobs j set state=?,error=?,updated_at=clock_timestamp(),
                    task_set_id=(select s.id from task_sets s join object_statuses os on os.id=s.trigger_status_id
                        where s.object_id=j.object_id and os.code=? and s.state='ACTIVE')
                where j.approval_request_id=? and j.state='AWAITING_APPROVAL'
                """,
                "APPROVED".equals(decision) ? "SUCCEEDED" : "FAILED",
                "APPROVED".equals(decision) ? null : "APPROVAL_" + decision,
                result.saved() ? result.status() : null,
                request);
    }

    private static ObjectTaskSetJob row(ResultSet r) throws SQLException {
        return new ObjectTaskSetJob(
                r.getObject("id", UUID.class),
                r.getObject("object_id", UUID.class),
                r.getObject("actor_id", UUID.class),
                ObjectTaskSetJob.State.valueOf(r.getString("state")),
                r.getObject("task_set_id", UUID.class),
                r.getObject("approval_request_id", UUID.class),
                r.getString("error"),
                r.getInt("attempts"),
                r.getTimestamp("created_at").toInstant());
    }

    private String encode(ObjectTaskSetCommand command) {
        try {
            return json.writeValueAsString(command);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot store task-set command", failure);
        }
    }

    private ObjectTaskSetCommand decode(String value) {
        try {
            return json.readValue(value, ObjectTaskSetCommand.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot read task-set command", failure);
        }
    }
}
