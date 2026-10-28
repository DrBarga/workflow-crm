package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import dev.workflowcrm.users.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserRegionAuthorityIT extends ObjectApiTestSupport {
    @Autowired
    EntityManager entities;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    ObjectAssignmentQuery assignments;

    @Autowired
    AuthenticationUserQuery authentication;

    @Test
    void cachedEntityCannotRestoreARevokedAssignment() {
        UserRegionTestData.assign(jdbc, lead, otherRegion);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var cached = entities.find(User.class, lead);
            assertThat(cached.getRegionIds()).contains(otherRegion);
            jdbc.update("delete from user_regions where user_id=? and region_id=?", lead, otherRegion);
            assertThat(assignments.member(lead).orElseThrow().regionIds()).containsExactly(region);
            assertThat(authentication.findById(lead).orElseThrow().regionIds()).containsExactly(region);
            status.setRollbackOnly();
        });
    }

    @Test
    void compatibilityRegionMatchesTheSameSortedSetAcrossProjections() {
        var high = java.util.UUID.fromString("f0000000-0000-0000-0000-000000000001");
        var low = java.util.UUID.fromString("10000000-0000-0000-0000-000000000001");
        jdbc.update("insert into regions(id,code,name) values (?,'HIGH','High'),(?,'LOW','Low')", high, low);
        UserRegionTestData.replace(jdbc, lead, low, high);
        var member = assignments.member(lead).orElseThrow();
        var account = authentication.findById(lead).orElseThrow();
        assertThat(member.regionIds()).containsExactly(high, low);
        assertThat(account.regionIds()).isEqualTo(member.regionIds());
        assertThat(account.regionId()).isEqualTo(member.regionIds().getFirst());
        assertThat(member.regionId()).isEqualTo(account.regionId());
    }
}
