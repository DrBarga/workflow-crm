package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCardFieldsIT extends ObjectCardTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"FOUND", "IN_PROGRESS"})
    void managerCanEditOrdinaryAndAddressFieldsBeforeCommittee(String status) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, status);
        var f = card(manager, object).path("editableFields");
        for (String field : List.of(
                "name", "address", "objectType", "technicalSpecifications", "commercialTerms", "contractTypeId"))
            assertThat(f.path(field).asBoolean()).as(field).isTrue();
        for (String field : List.of(
                "createdAt",
                "authorId",
                "status",
                "geoM",
                "entrance",
                "googleMapsUrl",
                "version",
                "managerId",
                "regionId")) assertThat(f.path(field).asBoolean()).as(field).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"IC_PREPARATION", "COMMERCIAL_TERMS_APPROVAL", "CONTRACT_APPROVAL", "CONTRACT_SIGNED", "OPENED"})
    void managerCanEditAddressAndTypeAtEveryStage(String status) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, status);
        var f = card(manager, object).path("editableFields");
        assertThat(f.path("name").asBoolean()).isTrue();
        assertThat(f.path("address").asBoolean()).isTrue();
        assertThat(f.path("objectType").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void privilegedEditorsCanEditProtectedAddressButNotSystemFields(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        var f = card(actor(role), object).path("editableFields");
        assertThat(f.path("address").asBoolean()).isTrue();
        assertThat(f.path("objectType").asBoolean()).isTrue();
        assertThat(f.path("createdAt").asBoolean()).isFalse();
        assertThat(f.path("entrance").asBoolean()).isFalse();
    }

    @Test
    void ceoIsReadOnly() throws Exception {
        var f = card(ceo, add("Об’єкт", manager, region)).path("editableFields");
        f.elements().forEachRemaining(v -> assertThat(v.asBoolean()).isFalse());
    }

    @Test
    void contractTypeRulesDependOnContractTemplateSet() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        assertThat(card(manager, object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isTrue();
        assertThat(card(contract, object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isTrue();
        assertThat(card(director, object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isTrue();
        UUID contractType = jdbc.queryForObject("select id from contract_types where code='PRIMARY'", UUID.class);
        ObjectContractTaskSetTestData.create(jdbc, object, contractType, null, "COMPLETED");
        for (UUID actor : List.of(manager, contract, lead, director, ceo))
            assertThat(card(actor, object)
                            .path("editableFields")
                            .path("contractTypeId")
                            .asBoolean())
                    .isFalse();
        assertThat(card(admin, object)
                        .path("editableFields")
                        .path("contractTypeId")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void contractRoleMayEditOnlyContractTypeSigningAndAcceptanceDates() throws Exception {
        var f = card(contract, add("Об’єкт", outsider, otherRegion)).path("editableFields");
        var allowed = new HashSet<String>();
        f.fields().forEachRemaining(e -> {
            if (e.getValue().asBoolean()) allowed.add(e.getKey());
        });
        assertThat(allowed)
                .containsExactlyInAnyOrder(
                        "contractTypeId",
                        "contractSignedDate",
                        "preliminaryContractSignedDate",
                        "commissioningDate",
                        "constructionAcceptancePlannedDate",
                        "constructionAcceptanceActualDate");
    }
}
