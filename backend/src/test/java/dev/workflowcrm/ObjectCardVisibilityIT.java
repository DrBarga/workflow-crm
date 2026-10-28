package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCardVisibilityIT extends ObjectCardTestSupport {
    @Test
    void ownCardContainsDetailsBlocksMilestonesAndVersionWithoutChangingObject() throws Exception {
        UUID object = add("Свій об’єкт", manager, region);
        contractType(object);
        jdbc.update(
                "update objects set version=7,construction_acceptance_planned_date='2026-10-31',construction_term_days=1,opening_planned_date='2026-11-01',opening_date='2026-10-05' where id=?",
                object);
        var c = card(manager, object);
        assertThat(c.path("id").asText()).isEqualTo(object.toString());
        assertThat(c.path("version").asInt()).isEqualTo(7);
        assertThat(c.path("contact").path("phone").asText()).isEqualTo("***");
        assertThat(c.path("technicalSpecifications").path("buildingArea").asInt())
                .isEqualTo(100);
        assertThat(c.path("commercialTerms").path("fixedRentAmount").asInt()).isEqualTo(9999);
        assertThat(c.path("workflow")
                        .path("milestones")
                        .path("openingPlannedDate")
                        .asText())
                .isEqualTo("2026-11-01");
        assertThat(c.path("workflow")
                        .path("milestones")
                        .path("openingActualDate")
                        .asText())
                .isEqualTo("2026-10-05");
        assertThat(c.path("googleMapsUrl").asText()).contains("30.5", "50.4");
        assertThat(c.path("pendingRequests")).isEmpty();
        cardRequest(manager, object).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isEqualTo(7);
        assertThat(count("event_outbox")).isZero();
        assertThat(count("object_history")).isZero();
    }

    @Test
    void foreignObjectsReturnExactlyTwoFieldsIncludingObjectsOutsideRegion() throws Exception {
        for (UUID owner : List.of(colleague, outsider)) {
            UUID object = add("Чужий", owner, owner.equals(outsider) ? otherRegion : region);
            assertThat(fields(card(manager, object))).containsExactlyInAnyOrder("name", "entrance");
        }
    }

    @Test
    void regionalLeadHasFullAssignedRegionsAndOnlyPinsElsewhere() throws Exception {
        UUID child = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values (?,'CARD_CHILD','Центр')", child);
        UserRegionTestData.assign(jdbc, lead, child);
        assertThat(card(lead, add("Дитина", manager, child)).has("address")).isTrue();
        assertThat(fields(card(lead, add("Чужий", outsider, otherRegion))))
                .containsExactlyInAnyOrder("name", "entrance");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO"})
    void contractTypeIsVisibleOnlyToExplicitlyAllowedFullRoles(String role) throws Exception {
        UUID object = add("Договір", manager, region);
        contractType(object);
        assertThat(card(actor(role), object)
                        .path("workflow")
                        .path("contractType")
                        .asText())
                .isEqualTo("PRIMARY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FOUND", "CONTRACT_APPROVAL", "REFUSED"})
    void contractRoleGetsOnlyPermittedDataThroughoutNetwork(String state) throws Exception {
        UUID object = add("Технічна картка", outsider, otherRegion);
        state(object, state);
        contractType(object);
        var c = card(contract, object);
        assertThat(fields(c))
                .containsExactlyInAnyOrder(
                        "name",
                        "entrance",
                        "technicalSpecifications",
                        "technicalDescription",
                        "contractType",
                        "contractTypeId",
                        "contractSignedDate",
                        "preliminaryContractSignedDate",
                        "commissioningDate",
                        "openingPlannedDate",
                        "constructionAcceptancePlannedDate",
                        "constructionAcceptanceActualDate",
                        "version",
                        "googleMapsUrl",
                        "availableTransitions",
                        "pendingRequests",
                        "editableFields");
        assertThat(c.path("contractType").asText()).isEqualTo("PRIMARY");
        assertThat(c.path("contractSignedDate").asText()).isEqualTo("2026-10-01");
        assertThat(c.toString()).doesNotContain("Приватна адреса", "+380991234567", "fixedRentAmount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN"})
    void shortCommentIsReturnedOnlyWithFullCard(String role) throws Exception {
        UUID object = add("Коментар", manager, region);
        String comment = "а".repeat(200);
        jdbc.update("update objects set short_comment=? where id=?", comment, object);
        assertThat(card(actor(role), object).path("shortComment").asText()).isEqualTo(comment);
        assertThat(card(contract, object).has("shortComment")).isFalse();
        assertThat(fields(card(outsider, object))).containsExactlyInAnyOrder("name", "entrance");
    }

    @Test
    void emptyShortCommentIsExplicitNullOnlyInFullCard() throws Exception {
        UUID object = add("Без коментаря", manager, region);
        var full = card(manager, object);
        assertThat(full.has("shortComment")).isTrue();
        assertThat(full.path("shortComment").isNull()).isTrue();
        assertThat(card(contract, object).has("shortComment")).isFalse();
    }

    @Test
    void currentDatabaseRoleIsUsedInsteadOfOldJwtClaims() throws Exception {
        UUID object = add("Свій", manager, region);
        jdbc.update(
                "update users set role_id=(select id from roles where code='CONTRACT_MANAGER') where id=?", manager);
        assertThat(card(manager, object).has("address")).isFalse();
    }
}
