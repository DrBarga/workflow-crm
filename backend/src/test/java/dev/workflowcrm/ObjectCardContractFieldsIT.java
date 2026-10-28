package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCardContractFieldsIT extends ObjectCardTestSupport {
    @Test
    void administratorGetsFullDataIncludingContractType() throws Exception {
        UUID object = add("Договір", outsider, otherRegion);
        contractType(object);
        var c = card(admin, object);
        assertThat(c.has("address")).isTrue();
        assertThat(c.path("workflow").path("contractType").asText()).isEqualTo("PRIMARY");
        assertThat(c.path("workflow").path("contractTypeId").asText())
                .isEqualTo(
                        jdbc.queryForObject("select id::text from contract_types where code='PRIMARY'", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FOUND", "CONTRACT_APPROVAL", "CONTRACT_SIGNED", "OPENED", "PAUSED", "REFUSED"})
    void contractRoleSeesAllPermittedDatesWithoutCommercialFields(String status) throws Exception {
        UUID object = add("Акт БМР", outsider, otherRegion);
        state(object, status);
        contractType(object);
        jdbc.update(
                """
                update objects set construction_acceptance_planned_date='2026-10-15',
                    construction_acceptance_actual_date='2026-10-16', construction_term_days=17, preliminary_contract_signed_date='2026-09-01', commissioning_date='2026-10-14',
                    opening_date='2026-11-02', payment_act_planned_date='2026-11-03',
                    payment_act_actual_date='2026-11-04', version=8 where id=?
                """,
                object);

        var c = card(contract, object);

        assertThat(c.path("constructionAcceptancePlannedDate").asText()).isEqualTo("2026-10-15");
        assertThat(c.path("constructionAcceptanceActualDate").asText()).isEqualTo("2026-10-16");
        assertThat(c.path("contractSignedDate").asText()).isEqualTo("2026-10-01");
        assertThat(c.path("preliminaryContractSignedDate").asText()).isEqualTo("2026-09-01");
        assertThat(c.path("commissioningDate").asText()).isEqualTo("2026-10-14");
        assertThat(c.path("openingPlannedDate").asText()).isEqualTo("2026-11-01");
        assertThat(fields(c))
                .doesNotContain(
                        "workflow",
                        "commercialTerms",
                        "contact",
                        "address",
                        "openingActualDate",
                        "paymentActPlannedDate",
                        "paymentActActualDate");
        assertThat(c.path("version").asLong()).isEqualTo(8);
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isEqualTo(8);
        assertThat(count("object_history")).isZero();
        assertThat(count("event_outbox")).isZero();
    }

    @Test
    void missingMilestonesReturnNullAcceptanceDates() throws Exception {
        var c = card(contract, add("Без дат", outsider, otherRegion));

        assertThat(c.has("constructionAcceptancePlannedDate")).isTrue();
        assertThat(c.has("constructionAcceptanceActualDate")).isTrue();
        assertThat(c.path("constructionAcceptancePlannedDate").isNull()).isTrue();
        assertThat(c.path("constructionAcceptanceActualDate").isNull()).isTrue();
        assertThat(c.path("contractSignedDate").isNull()).isTrue();
        for (String field :
                java.util.List.of("preliminaryContractSignedDate", "commissioningDate", "openingPlannedDate")) {
            assertThat(c.has(field)).as(field).isTrue();
            assertThat(c.path(field).isNull()).as(field).isTrue();
        }
    }

    @Test
    void plannedAcceptanceDateDoesNotRequireActualDate() throws Exception {
        UUID object = add("План БМР", outsider, otherRegion);
        jdbc.update("update objects set construction_acceptance_planned_date='2026-10-15' where id=?", object);

        var c = card(contract, object);

        assertThat(c.path("constructionAcceptancePlannedDate").asText()).isEqualTo("2026-10-15");
        assertThat(c.path("constructionAcceptanceActualDate").isNull()).isTrue();
    }

    @Test
    void administratorSeesLegacyContractTypeWithoutDirectoryReference() throws Exception {
        UUID object = add("Історичний договір", outsider, otherRegion);
        jdbc.update("update objects set contract_type='Історичний тип' where id=?", object);

        var workflow = card(admin, object).path("workflow");

        assertThat(workflow.path("contractType").asText()).isEqualTo("Історичний тип");
        assertThat(workflow.has("contractTypeId")).isFalse();
    }

    @Test
    void foreignManagerAndLeadCannotReadContractFieldsOrAcceptanceDates() throws Exception {
        UUID object = add("Чужий акт", outsider, otherRegion);
        contractType(object);
        jdbc.update("update objects set construction_acceptance_actual_date='2026-10-16' where id=?", object);

        for (UUID actor : new UUID[] {manager, lead}) {
            assertThat(fields(card(actor, object))).containsExactlyInAnyOrder("name", "entrance");
        }
    }
}
