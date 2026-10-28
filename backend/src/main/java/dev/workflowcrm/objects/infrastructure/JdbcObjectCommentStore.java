package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcObjectCommentStore implements ObjectCommentStore {
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private static final String SELECT =
            "select c.*,u.full_name from object_comments c join users u on u.id=c.author_id ";
    private final JdbcTemplate jdbc;

    JdbcObjectCommentStore(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long snapshot(UUID object) {
        return jdbc.queryForObject(
                "select coalesce(max(sequence),0) from object_comments where object_id=?", Long.class, object);
    }

    public List<Row> page(UUID object, long after, long snapshot, int limit) {
        return jdbc.query(
                SELECT + "where c.object_id=? and c.sequence>? and c.sequence<=? order by c.sequence limit ?",
                (r, n) -> new Row(r.getLong("sequence"), map(r)),
                object,
                after,
                snapshot,
                limit);
    }

    public ObjectComment get(UUID object, UUID id) {
        return jdbc.query(SELECT + "where c.object_id=? and c.id=?", (r, n) -> map(r), object, id).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    }

    public ObjectComment create(UUID object, UUID actor, String text, List<ObjectComment.Mention> mentions) {
        UUID id = jdbc.queryForObject(
                "insert into object_comments(object_id,author_id,body,mentions) values(?,?,?,?::jsonb) returning id",
                UUID.class,
                object,
                actor,
                text,
                encode(mentions));
        return get(object, id);
    }

    public ObjectComment edit(UUID object, UUID id, String text, List<ObjectComment.Mention> mentions) {
        jdbc.update(
                "update object_comments set body=?,mentions=?::jsonb where object_id=? and id=?",
                text,
                encode(mentions),
                object,
                id);
        return get(object, id);
    }

    public void delete(UUID object, UUID id) {
        jdbc.update("delete from object_comments where object_id=? and id=?", object, id);
    }

    private ObjectComment map(ResultSet r) throws SQLException {
        var edited = r.getTimestamp("edited_at");
        return new ObjectComment(
                r.getObject("id", UUID.class),
                new ObjectComment.Author(r.getObject("author_id", UUID.class), r.getString("full_name")),
                r.getString("body"),
                r.getTimestamp("created_at").toInstant(),
                edited == null ? null : edited.toInstant(),
                edited != null,
                edited == null ? null : "відредаговано",
                r.getLong("version"),
                decode(r.getString("mentions")));
    }

    private String encode(List<ObjectComment.Mention> mentions) {
        try {
            return json.writeValueAsString(mentions);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode comment mentions", failure);
        }
    }

    private List<ObjectComment.Mention> decode(String value) throws SQLException {
        try {
            return json.readValue(
                    value, new com.fasterxml.jackson.core.type.TypeReference<List<ObjectComment.Mention>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new SQLException("Invalid stored comment mentions", failure);
        }
    }
}
