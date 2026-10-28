package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectListScopeTest {
    final UUID actor = UUID.randomUUID(), own = UUID.randomUUID(), foreign = UUID.randomUUID();

    @Test
    void leadFullAccessDependsOnObjectRegionRatherThanManager() {
        var scope = new ObjectListScope(actor, "REGIONAL_MANAGER", List.of(own));
        assertThat(scope.full(UUID.randomUUID(), own)).isTrue();
        assertThat(scope.full(actor, foreign)).isFalse();
        assertThat(scope.regionRestricted()).isFalse();
    }

    @Test
    void managerNeedsOwnershipAndRemainsRestrictedToAssignedRegions() {
        var scope = new ObjectListScope(actor, "MANAGER", List.of(own, foreign));
        assertThat(scope.full(actor, foreign)).isTrue();
        assertThat(scope.full(UUID.randomUUID(), own)).isFalse();
        assertThat(scope.regionRestricted()).isTrue();
    }

    @Test
    void onlyKnownNetworkRolesHaveUnconditionalFullProjection() {
        for (String role : List.of("DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN"))
            assertThat(new ObjectListScope(actor, role, List.of()).full(null, foreign))
                    .isTrue();
        for (String role : List.of("CONTRACT_MANAGER", "UNKNOWN", "REGIONAL_MANAGER"))
            assertThat(new ObjectListScope(actor, role, List.of()).full(actor, foreign))
                    .isFalse();
    }

    @Test
    void scopeIsImmutableAndOrderIndependentForCursorAndExportChecks() {
        var regions = new ArrayList<>(List.of(own, foreign, own));
        var scope = new ObjectListScope(actor, "REGIONAL_MANAGER", regions);
        regions.clear();
        assertThat(scope).isEqualTo(new ObjectListScope(actor, "REGIONAL_MANAGER", List.of(foreign, own)));
    }
}
