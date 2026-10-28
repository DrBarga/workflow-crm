package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ObjectSchemaChecks5IT extends ObjectSchemaTestSupport {
    @Test
    void requiresAtLeastOnePositiveAreaAtDatabaseLevel() {
        var object = objects.saveAndFlush(newObject());
        assertThatThrownBy(() -> jdbc.update(
                        "update objects set object_area = null, land_area = null where id = ?", object.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void requiresContactChannelAtDatabaseLevel() {
        var object = objects.saveAndFlush(newObject());
        assertThatThrownBy(() -> jdbc.update(
                        "update objects set contact_phone = ' ', contact_email = null where id = ?", object.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void requiresConditionsForConditionalCommitteeApproval() {
        var object = objects.saveAndFlush(newObject());
        assertThatThrownBy(() -> jdbc.update(
                        "update objects set investment_committee_decision = 'APPROVED_WITH_CONDITIONS', "
                                + "investment_committee_date = current_date where id = ?",
                        object.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void persistsCommitteeRefusalAndSigningAndOpeningDates() {
        var object = objects.saveAndFlush(newObject());
        jdbc.update(
                "update objects set investment_committee_decision = 'REFUSED', investment_committee_date = date '2026-10-01', "
                        + "refusal_reason_id = (select id from refusal_reasons where code = 'IC_NOT_APPROVED'), "
                        + "contract_signed_date = date '2026-10-02', opening_date = date '2026-10-03' where id = ?",
                object.getId());
        entityManager.clear();
        var persisted = objects.findById(object.getId()).orElseThrow();
        assertThat(persisted.getInvestmentCommitteeDate()).isEqualTo("2026-10-01");
        assertThat(persisted.getRefusalReason().getCode()).isEqualTo("IC_NOT_APPROVED");
        assertThat(persisted.getContractSignedDate()).isEqualTo("2026-10-02");
        assertThat(persisted.getOpeningDate()).isEqualTo("2026-10-03");
    }

    @Test
    void preventsDanglingManagerAndDictionaryReferences() {
        var object = objects.saveAndFlush(newObject());
        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", object.getManagerId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void preventsDuplicateDictionaryCodes() {
        assertThatThrownBy(() -> jdbc.update("insert into object_types(code, name) values ('LAND_PLOT', 'Duplicate')"))
                .isInstanceOf(DataAccessException.class);
    }
}
