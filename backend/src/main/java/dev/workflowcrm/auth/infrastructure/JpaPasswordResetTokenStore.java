package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.PasswordResetToken;
import dev.workflowcrm.auth.domain.PasswordResetTokenStore;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaPasswordResetTokenStore implements PasswordResetTokenStore {
    private final EntityManager entities;

    JpaPasswordResetTokenStore(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public void invalidateForUser(UUID userId, Instant invalidatedAt) {
        entities.createNativeQuery(
                        "update password_reset_tokens set invalidated_at=:now where user_id=:user and consumed_at is null and invalidated_at is null")
                .setParameter("now", invalidatedAt)
                .setParameter("user", userId)
                .executeUpdate();
    }

    @Override
    public void save(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        entities.createNativeQuery(
                        "insert into password_reset_tokens(id,user_id,token_hash,expires_at) values(:id,:user,:hash,:expires)")
                .setParameter("id", id)
                .setParameter("user", userId)
                .setParameter("hash", tokenHash)
                .setParameter("expires", expiresAt)
                .executeUpdate();
    }

    @Override
    public Optional<PasswordResetToken> lock(String tokenHash) {
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Object[]>) entities.createNativeQuery(
                        "select id,user_id,expires_at,consumed_at,invalidated_at from password_reset_tokens where token_hash=:hash for update")
                .setParameter("hash", tokenHash)
                .getResultList();
        return rows.stream()
                .findFirst()
                .map(row -> new PasswordResetToken(
                        (UUID) row[0], (UUID) row[1], instant(row[2]), instant(row[3]), instant(row[4])));
    }

    @Override
    public void consume(UUID id, Instant consumedAt) {
        entities.createNativeQuery("update password_reset_tokens set consumed_at=:now where id=:id")
                .setParameter("now", consumedAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private static Instant instant(Object value) {
        if (value == null || value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported password reset timestamp type");
    }
}
