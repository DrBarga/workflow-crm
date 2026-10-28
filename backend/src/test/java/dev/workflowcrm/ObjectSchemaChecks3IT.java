package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.workflowcrm.objects.domain.TransitionRequiredField;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ObjectSchemaChecks3IT extends ObjectSchemaTestSupport {
    @Test
    void allowsLateRefusalAndRestorationOnlyToLeadershipWithRequiredDates() {
        for (String code : java.util.List.of("LEAD_REFUSE_IC", "LEAD_REFUSE_COMMERCIAL", "LEAD_REFUSE_PAUSED")) {
            var rule = transitions.findByCode(code).orElseThrow();
            assertThat(rule.getRoleIds())
                    .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
            assertThat(rule.getRequiredFields()).containsExactly(TransitionRequiredField.REFUSAL_REASON);
        }
        for (String code : java.util.List.of(
                "RESTORE_REFUSED_IC",
                "RESTORE_REFUSED_COMMERCIAL",
                "RESTORE_REFUSED_CONTRACT",
                "RESTORE_REFUSED_SIGNED",
                "RESTORE_REFUSED_OPENED")) {
            assertThat(transitions.findByCode(code).orElseThrow().getRoleIds())
                    .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        }
        assertThat(transitions
                        .findByCode("RESTORE_REFUSED_OPENED")
                        .orElseThrow()
                        .getRequiredFields())
                .containsExactlyInAnyOrder(
                        TransitionRequiredField.CONTRACT_SIGNED_DATE,
                        TransitionRequiredField.CONTRACT_TYPE,
                        TransitionRequiredField.OPENING_DATE);
    }

    @Test
    void rejectsFixedCommitteeReasonOnUnrelatedTransition() {
        assertThatThrownBy(
                        () -> jdbc.update(
                                "update object_status_transitions set fixed_refusal_reason_id = "
                                        + "(select id from refusal_reasons where code = 'IC_NOT_APPROVED') where code = 'SIGN_CONTRACT'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void persistsObjectWithAllRequiredFieldsAndIncrementsVersionOnUpdate() {
        var object = objects.saveAndFlush(newObject());
        long firstVersion = object.getVersion();
        var createdAt = jdbc.queryForObject(
                        "select created_at from objects where id = ?", java.sql.Timestamp.class, object.getId())
                .toInstant();
        object.rename("Updated property");
        object.describeCommercialTerms("Rent is under discussion", "Lease");
        object.describeTechnicalSpecifications("Technical survey is pending");
        objects.flush();
        assertThat(object.getVersion()).isEqualTo(firstVersion + 1);
        entityManager.clear();
        var persisted = objects.findById(object.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Updated property");
        assertThat(persisted.getEntrance().getLongitude()).isEqualByComparingTo("30.5234000");
        assertThat(persisted.getContact().getRole()).isEqualTo("Owner");
        assertThat(persisted.getSpecifications().getCommercialTerms()).isEqualTo("Rent is under discussion");
        assertThat(persisted.getSpecifications().getTechnicalSpecifications()).isEqualTo("Technical survey is pending");
        assertThat(persisted.getSpecifications().getContractType()).isEqualTo("Lease");
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getVersion()).isEqualTo(firstVersion + 1);
    }
}
