package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.api.*;
import dev.workflowcrm.files.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JdbcStoredFileStore implements StoredFileStore {
    private final JdbcTemplate jdbc;

    JdbcStoredFileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireCategory(UUID category) {
        var active = jdbc.queryForList(
                "select is_active from file_categories where id=? for share", Boolean.class, category);
        if (active.isEmpty() || !active.getFirst())
            throw new UnprocessableInputException("categoryId", "Active file category required");
    }

    public StoredFile create(
            FileOwner owner,
            UUID author,
            UUID category,
            FileUpload upload,
            FileUploadReservations.Reservation reservation) {
        jdbc.update(
                "insert into files(id,object_id,task_id,author_id,category_id,bucket,storage_key,original_name,content_type,size_bytes) values(?,?,?,?,?,?,?,?,?,?)",
                reservation.id(),
                owner.type() == FileOwner.Type.OBJECT ? owner.id() : null,
                owner.type() == FileOwner.Type.TASK ? owner.id() : null,
                author,
                category,
                reservation.bucket(),
                reservation.key(),
                upload.name(),
                upload.contentType(),
                upload.content().length);
        return get(owner, reservation.id());
    }

    public StoredFile get(FileOwner owner, UUID id) {
        String column = owner.type() == FileOwner.Type.OBJECT ? "object_id" : "task_id";
        var rows = jdbc.query(
                "select * from files where " + column + "=? and id=? and deleted_at is null",
                (rs, n) -> new StoredFile(
                        rs.getObject("id", UUID.class),
                        owner,
                        rs.getObject("author_id", UUID.class),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("bucket"),
                        rs.getString("storage_key"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes"),
                        rs.getTimestamp("created_at").toInstant()),
                owner.id(),
                id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        return rows.getFirst();
    }
}
