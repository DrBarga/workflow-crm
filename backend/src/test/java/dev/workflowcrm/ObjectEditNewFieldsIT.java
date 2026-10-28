package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditNewFieldsIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "SUPERADMIN"})
    void permittedRolesSetAndClearCommissioningDate(String role) throws Exception {
        UUID object = add("Commissioning", manager, region);
        UUID actor = actor(role);
        assertThat(card(actor, object)
                        .path("editableFields")
                        .path("commissioningDate")
                        .asBoolean())
                .isTrue();
        patch(actor, object, change(object, "commissioningDate", "2026-11-01")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select commissioning_date::text from objects where id=?", String.class, object))
                .isEqualTo("2026-11-01");
        patch(actor, object, change(object, "commissioningDate", null)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select commissioning_date from objects where id=?", java.time.LocalDate.class, object))
                .isNull();
        assertThat(version(object)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name='commissioningDate'",
                        Integer.class,
                        object))
                .isEqualTo(2);
    }

    @Test
    void managerCannotSetOrClearCommissioningDateAndMixedPatchIsAtomic() throws Exception {
        UUID object = add("Restricted date", manager, region);
        assertThat(card(manager, object)
                        .path("editableFields")
                        .path("commissioningDate")
                        .asBoolean())
                .isFalse();
        for (String value : List.of("2026-11-01", "clear")) {
            var body = change(object, "commissioningDate", value.equals("clear") ? null : value);
            body.put("name", "Must not be saved");
            patch(manager, object, body).andExpect(status().isForbidden());
        }
        assertThat(version(object)).isZero();
        assertThat(jdbc.queryForObject("select name from objects where id=?", String.class, object))
                .isEqualTo("Restricted date");
        assertThat(count("object_history")).isZero();
    }

    @Test
    void commissioningDateScopeIsRegionalExceptForNetworkRoles() throws Exception {
        UUID object = add("Foreign commissioning", outsider, otherRegion);
        patch(lead, object, change(object, "commissioningDate", "2026-11-01")).andExpect(status().isForbidden());
        patch(director, object, change(object, "commissioningDate", "2026-11-01"))
                .andExpect(status().isOk());
        patch(contract, object, change(object, "commissioningDate", "2026-11-02"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void shortCommentUsesGeneralPermissionsAndValidatesLength(String role) throws Exception {
        UUID object = add("Comment", manager, region);
        UUID actor = actor(role);
        String text = "x".repeat(200);
        patch(actor, object, change(object, "shortComment", text)).andExpect(status().isOk());
        var error = response(patch(actor, object, change(object, "shortComment", text + "x"))
                .andExpect(status().isUnprocessableEntity()));
        assertThat(error.path("violations").toString()).contains("shortComment");
        assertThat(jdbc.queryForObject("select short_comment from objects where id=?", String.class, object))
                .isEqualTo(text);
        assertThat(version(object)).isEqualTo(1);
        patch(actor, object, change(object, "shortComment", null)).andExpect(status().isOk());
        assertThat(version(object)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name='shortComment'",
                        Integer.class,
                        object))
                .isEqualTo(2);
    }

    @Test
    void shortCommentRejectsForeignObjectsContractRoleCeoAndStaleVersion() throws Exception {
        UUID object = add("Comment scope", manager, region);
        for (UUID actor : List.of(contract, ceo, outsider))
            patch(actor, object, change(object, "shortComment", "Denied")).andExpect(status().isForbidden());
        patch(manager, object, change(object, "shortComment", "First edit")).andExpect(status().isOk());
        patch(lead, object, java.util.Map.of("version", 0, "shortComment", "Stale edit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(jdbc.queryForObject("select short_comment from objects where id=?", String.class, object))
                .isEqualTo("First edit");
    }

    @Test
    void contractRoleSetsPreliminaryDateWithoutChangingStatus() throws Exception {
        UUID object = add("Preliminary contract", outsider, otherRegion);
        patch(contract, object, change(object, "preliminaryContractSignedDate", "2026-10-23"))
                .andExpect(status().isOk());
        assertThat(state(object)).isEqualTo("FOUND");
        assertThat(jdbc.queryForObject(
                        "select preliminary_contract_signed_date::text from objects where id=?", String.class, object))
                .isEqualTo("2026-10-23");
    }
}
