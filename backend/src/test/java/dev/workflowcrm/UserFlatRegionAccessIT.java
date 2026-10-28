package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.auth.api.AccessScope;
import dev.workflowcrm.auth.api.ResourceScope;
import dev.workflowcrm.auth.domain.ResourceAccessPolicy;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserFlatRegionAccessIT extends ObjectCardTestSupport {
    @Autowired
    ObjectAssignmentQuery assignments;

    @Autowired
    AuthenticationUserQuery authentication;

    @Autowired
    ResourceAccessPolicy policy;

    @Test
    void everyExplicitRegionGrantsAccessAndRevocationTakesEffectImmediately() throws Exception {
        UUID object = add("Second region", outsider, otherRegion);
        assertThat(fields(card(lead, object))).containsExactlyInAnyOrder("name", "entrance");
        UserRegionTestData.assign(jdbc, lead, otherRegion);
        assertThat(card(lead, object).has("workflow")).isTrue();
        assertThat(assignments.member(lead).orElseThrow().regionIds()).containsExactlyInAnyOrder(region, otherRegion);
        assertThat(authentication.findById(lead).orElseThrow().regionIds())
                .containsExactlyInAnyOrder(region, otherRegion);
        assertThat(policy.allows(
                        authentication.findById(lead).orElseThrow(),
                        new ResourceScope(null, otherRegion),
                        Set.of("REGIONAL_MANAGER"),
                        AccessScope.REGION))
                .isTrue();
        UserRegionTestData.replace(jdbc, lead, region);
        assertThat(fields(card(lead, object))).containsExactlyInAnyOrder("name", "entrance");
        assertThat(authentication.findById(lead).orElseThrow().belongsTo(otherRegion))
                .isFalse();
        assertThat(policy.allows(
                        authentication.findById(lead).orElseThrow(),
                        new ResourceScope(null, otherRegion),
                        Set.of("REGIONAL_MANAGER"),
                        AccessScope.REGION))
                .isFalse();
    }

    @Test
    void flatDirectoryHasNoInheritedMembership() {
        assertThat(assignments.regionContains(region, otherRegion)).isFalse();
        assertThat(assignments.regionContains(region, region)).isTrue();
        assertThat(assignments.regionTree(region)).containsExactly(region);
        assertThat(assignments.regionTree(UUID.randomUUID())).isEmpty();
    }
}
