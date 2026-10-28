package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.domain.FileUploadReservations;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcFileUploadReservations implements FileUploadReservations {
    private final JdbcTemplate jdbc;

    JdbcFileUploadReservations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String bucket, dev.workflowcrm.files.api.FileOwner owner) {
        UUID id = UUID.randomUUID();
        String key = owner.prefix() + id;
        jdbc.update("insert into file_upload_reservations(id,bucket,storage_key) values(?,?,?)", id, bucket, key);
        return new Reservation(id, bucket, key);
    }

    public void lock(Reservation reservation) {
        var ids = jdbc.queryForList(
                "select id from file_upload_reservations where id=? and bucket=? and storage_key=? for update",
                UUID.class,
                reservation.id(),
                reservation.bucket(),
                reservation.key());
        if (ids.isEmpty()) throw new IllegalStateException("File upload reservation expired");
    }

    public void complete(UUID id) {
        jdbc.update("delete from file_upload_reservations where id=?", id);
    }

    public Optional<Reservation> expired() {
        return jdbc
                .query(
                        "select r.id,r.bucket,r.storage_key from file_upload_reservations r where r.next_attempt_at<=clock_timestamp() and not exists (select 1 from files f where f.bucket=r.bucket and f.storage_key=r.storage_key) order by r.next_attempt_at,r.id limit 1 for update of r skip locked",
                        (rs, n) -> new Reservation(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .stream()
                .findFirst();
    }

    public void retry(UUID id) {
        jdbc.update(
                "update file_upload_reservations set attempts=attempts+1,next_attempt_at=clock_timestamp()+interval '1 minute' where id=?",
                id);
    }
}
