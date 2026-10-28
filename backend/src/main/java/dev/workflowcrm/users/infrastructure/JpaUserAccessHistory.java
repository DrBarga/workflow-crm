package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.UserAccessState;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserAccessHistory {
    private final EntityManager entities;

    JpaUserAccessHistory(EntityManager entities) {
        this.entities = entities;
    }

    public void recordChange(UUID actorId, UUID userId, UserAccessState before, UserAccessState after) {
        entities.createNativeQuery(
                        """
            insert into user_access_history (id, actor_id, user_id, action, old_role, new_role,
                old_region_id, new_region_id, old_status, new_status, old_region_ids, new_region_ids)
            values (:id, :actor, :user, :action, :oldRole, :newRole, :oldRegion, :newRegion, :oldStatus, :newStatus, :oldRegions, :newRegions)
            """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("actor", actorId)
                .setParameter("user", userId)
                .setParameter("action", before == null ? "CREATED" : "ACCESS_CHANGED")
                .setParameter("oldRole", before == null ? null : before.role().name())
                .setParameter("newRole", after.role().name())
                .setParameter("oldRegion", before == null ? null : before.regionId())
                .setParameter("newRegion", after.regionId())
                .setParameter(
                        "oldStatus", before == null ? null : before.status().name())
                .setParameter("newStatus", after.status().name())
                .setParameter(
                        "oldRegions",
                        before == null ? new UUID[0] : before.regionIds().toArray(UUID[]::new))
                .setParameter("newRegions", after.regionIds().toArray(UUID[]::new))
                .executeUpdate();
    }
}
