package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.auth.api.AccessScope;
import dev.workflowcrm.auth.api.ResourceScope;
import dev.workflowcrm.users.api.AuthenticationUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceAccessPolicyTest {
    private final ResourceAccessPolicy policy = new ResourceAccessPolicy();
    private final UUID userId = UUID.randomUUID();
    private final UUID regionId = UUID.randomUUID();

    @Test
    void ownershipRequiresBothAnAllowedRoleAndMatchingOwner() {
        var resource = new ResourceScope(userId, regionId);
        assertThat(policy.allows(user("MANAGER", regionId, true), resource, Set.of("MANAGER"), AccessScope.OWNER))
                .isTrue();
        assertThat(policy.allows(user("CEO", regionId, true), resource, Set.of("MANAGER"), AccessScope.OWNER))
                .isFalse();
        assertThat(policy.allows(
                        user("MANAGER", regionId, true),
                        new ResourceScope(UUID.randomUUID(), regionId),
                        Set.of("MANAGER"),
                        AccessScope.OWNER))
                .isFalse();
    }

    @Test
    void regionScopeNeverTreatsAnUnassignedRegionAsGlobalAccess() {
        var resource = new ResourceScope(userId, regionId);
        assertThat(policy.allows(
                        user("REGIONAL_MANAGER", regionId, true),
                        resource,
                        Set.of("REGIONAL_MANAGER"),
                        AccessScope.REGION))
                .isTrue();
        for (var user : java.util.List.of(
                user("REGIONAL_MANAGER", null, true), user("REGIONAL_MANAGER", UUID.randomUUID(), true))) {
            assertThat(policy.allows(user, resource, Set.of("REGIONAL_MANAGER"), AccessScope.REGION))
                    .isFalse();
        }
    }

    @Test
    void networkAccessIsExplicitAndDoesNotRequireARegion() {
        assertThat(policy.allows(
                        user("CEO", null, true), new ResourceScope(null, null), Set.of("CEO"), AccessScope.NETWORK))
                .isTrue();
        assertThat(policy.allows(
                        user("SUPERADMIN", null, true),
                        new ResourceScope(null, null),
                        Set.of("CEO"),
                        AccessScope.NETWORK))
                .isFalse();
    }

    @Test
    void missingRulesAndDisabledAccountsDenyAccess() {
        var resource = new ResourceScope(userId, regionId);
        var user = user("MANAGER", regionId, true);
        assertThat(policy.allows(user, resource, Set.of(), AccessScope.OWNER)).isFalse();
        assertThat(policy.allows(user, resource, null, AccessScope.OWNER)).isFalse();
        assertThat(policy.allows(user, null, Set.of("MANAGER"), AccessScope.OWNER))
                .isFalse();
        assertThat(policy.allows(user, resource, Set.of("MANAGER"), null)).isFalse();
        assertThat(policy.allows(null, resource, Set.of("MANAGER"), AccessScope.OWNER))
                .isFalse();
        assertThat(policy.allows(user("MANAGER", regionId, false), resource, Set.of("MANAGER"), AccessScope.OWNER))
                .isFalse();
    }

    private AuthenticationUser user(String role, UUID region, boolean active) {
        return new AuthenticationUser(userId, "hash", "User", role, region, active);
    }
}
