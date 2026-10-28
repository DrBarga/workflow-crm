package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.api.ObjectFile;
import dev.workflowcrm.files.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcObjectFileStore implements ObjectFileStore {
    private static final String SELECT =
            """
            select f.*,c.code category_code,c.name category_name,coalesce(c.is_restricted,false) restricted,
                   u.full_name author_name from files f left join file_categories c on c.id=f.category_id
                   join users u on u.id=f.author_id
            """;
    private final JdbcTemplate jdbc;

    JdbcObjectFileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Category category(UUID id, boolean lock) {
        var rows = jdbc.query(
                "select id,code,name,is_restricted,is_active from file_categories where id=?"
                        + (lock ? " for share" : ""),
                (rs, n) -> new Category(
                        new ObjectFile.Category(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                        rs.getBoolean(4),
                        rs.getBoolean(5)),
                id);
        if (rows.isEmpty() || !rows.getFirst().active())
            throw new UnprocessableInputException("categoryId", "Active file category required");
        return rows.getFirst();
    }

    public Row get(UUID objectId, UUID fileId) {
        var rows = jdbc.query(
                SELECT + " where f.object_id=? and f.id=? and f.deleted_at is null",
                (rs, n) -> row(rs),
                objectId,
                fileId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        return rows.getFirst();
    }

    public long snapshot(UUID objectId) {
        return jdbc.queryForObject(
                "select coalesce(max(sequence),0) from files where object_id=?", Long.class, objectId);
    }

    public List<Row> page(UUID objectId, boolean restrictedVisible, long after, long snapshot, int limit) {
        return jdbc.query(
                SELECT + " where f.object_id=? and f.deleted_at is null and (? or not coalesce(c.is_restricted,false))"
                        + " and f.sequence>? and f.sequence<=? order by f.sequence limit ?",
                (rs, n) -> row(rs),
                objectId,
                restrictedVisible,
                after,
                snapshot,
                limit);
    }

    public void delete(UUID objectId, UUID fileId) {
        jdbc.update(
                "update files set deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 where object_id=? and id=? and deleted_at is null",
                objectId,
                fileId);
    }

    private Row row(ResultSet rs) throws SQLException {
        UUID category = rs.getObject("category_id", UUID.class);
        var file = new ObjectFile(
                rs.getObject("id", UUID.class),
                category == null
                        ? null
                        : new ObjectFile.Category(
                                category, rs.getString("category_code"), rs.getString("category_name")),
                new ObjectFile.Author(rs.getObject("author_id", UUID.class), rs.getString("author_name")),
                rs.getString("original_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getTimestamp("created_at").toInstant(),
                false);
        return new Row(
                file,
                rs.getString("bucket"),
                rs.getString("storage_key"),
                rs.getBoolean("restricted"),
                rs.getLong("sequence"));
    }
}
