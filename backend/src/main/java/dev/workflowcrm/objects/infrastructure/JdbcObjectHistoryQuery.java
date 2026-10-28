package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectHistoryQuery implements ObjectHistoryQuery {
    private final JdbcTemplate jdbc;

    JdbcObjectHistoryQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ObjectHistoryPage.Entry> page(UUID object, long after, int limit) {
        return jdbc.query(
                """
                select h.*,u.full_name from object_history h
                left join users u on u.id=h.author_id
                where h.object_id=? and h.sequence>? order by h.sequence limit ?
                """,
                (r, n) -> {
                    var type = ObjectHistory.ActorType.valueOf(r.getString("actor_type"));
                    var author = new ObjectHistoryPage.Author(
                            r.getObject("author_id", UUID.class),
                            type,
                            type == ObjectHistory.ActorType.SYSTEM ? "Система" : r.getString("full_name"));
                    return new ObjectHistoryPage.Entry(
                            r.getObject("id", UUID.class),
                            r.getLong("sequence"),
                            r.getTimestamp("created_at").toInstant(),
                            author,
                            r.getString("field_name"),
                            r.getString("previous_value"),
                            r.getString("new_value"),
                            r.getLong("object_version"));
                },
                object,
                after,
                limit);
    }
}
