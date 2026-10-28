package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.integrations.api.ReverseGeocoder;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectImportIT extends ObjectImportTestSupport {
    @Test
    void queuedLegacyRowRejectsOpeningPlanThatDisagreesWithConstructionInputs() throws Exception {
        var value = row();
        value.put("constructionAcceptancePlannedDate", "2026-10-23");
        value.put("commercialTerms.constructionTermDays", "2");
        value.put("openingPlannedDate", "2026-10-26");
        var id = importService
                .request(
                        admin, "a".repeat(64), List.of(new dev.workflowcrm.objects.domain.ObjectImport.Input(2, value)))
                .id();
        imports.processNext();
        assertThat(state(id).path("rejected").asInt()).isEqualTo(1);
        assertThat(report(id).path("content").get(0).path("errors").toString()).contains("openingPlannedDate");
    }

    @Test
    void importsSpecificationsPlannedDatesContactsAndRecordsHistory() throws Exception {
        var row = row();
        row.putAll(Map.of(
                "technicalSpecifications.buildingArea",
                "500.25",
                "commercialTerms.fixedRentAmount",
                "4000.50",
                "commercialTerms.leaseTerms",
                "Умови оренди",
                "constructionAcceptancePlannedDate",
                "2026-11-05",
                "paymentActPlannedDate",
                "2026-12-05",
                "contact.name",
                "Імпорт Контакт",
                "contact.role",
                "Власник",
                "contact.phone",
                "+380991234567"));
        row.put("commercialTerms.constructionTermDays", "26");
        UUID id = enqueue(List.of(row));
        assertThat(imports.processNext()).isTrue();
        var result = state(id);
        assertThat(result.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("created").asInt()).isEqualTo(1);
        var object = jdbc.queryForMap(
                "select name,author_id,manager_id,building_area,fixed_rent_amount,construction_acceptance_planned_date,opening_planned_date,payment_act_planned_date,opening_date,contract_signed_date,investment_committee_date,version,(select code from object_statuses where id=status_id) as status from objects");
        assertThat(object.get("author_id")).isEqualTo(admin);
        assertThat(object.get("manager_id")).isEqualTo(manager);
        assertThat(object.get("status")).isEqualTo("FOUND");
        assertThat(object.get("version")).isEqualTo(0L);
        assertThat(object.get("building_area").toString()).isEqualTo("500.25");
        assertThat(object.get("fixed_rent_amount").toString()).isEqualTo("4000.50");
        assertThat(object.get("opening_planned_date").toString()).isEqualTo("2026-12-01");
        assertThat(object.get("construction_acceptance_planned_date").toString())
                .isEqualTo("2026-11-05");
        assertThat(object.get("payment_act_planned_date").toString()).isEqualTo("2026-12-05");
        assertThat(object.get("opening_date")).isNull();
        assertThat(object.get("contract_signed_date")).isNull();
        assertThat(object.get("investment_committee_date")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from object_contacts", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from object_history where field_name='CREATED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from object_import_audit", Integer.class))
                .isEqualTo(2);
        assertThat(report(id).path("content").get(0).path("row").asInt()).isEqualTo(2);
    }

    @Test
    void validatesWholeFileBeforeCreatingAnyObject() throws Exception {
        var second = row();
        second.remove("address");
        second.put("name", "Друга назва");
        when(geocoder.resolve(any(), any())).thenAnswer(call -> {
            assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                    .isZero();
            return new ReverseGeocoder.Location("Київ", "UA-30", "Київ");
        });
        UUID id = enqueue(List.of(row(), second));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(2);
    }

    @Test
    void blankNameUsesCoordinateAddressEvenIfManualAddressIsPresent() throws Exception {
        var row = row();
        row.remove("name");
        UUID id = enqueue(List.of(row));
        imports.processNext();
        assertThat(jdbc.queryForObject("select name from objects", String.class))
                .isEqualTo("Хрещатик, Київ");
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
    }

    @Test
    void resolvesMissingRegionAndAddress() throws Exception {
        var row = row();
        row.remove("regionId");
        row.remove("address");
        UUID id = enqueue(List.of(row));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select address from objects", String.class))
                .isEqualTo("Хрещатик, Київ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"entrance.latitude", "managerId", "objectArea"})
    void missingRequiredFieldsRejectOnlyTheirRows(String field) throws Exception {
        var bad = row();
        bad.remove(field);
        UUID id = enqueue(List.of(row(), bad));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(state(id).path("rejected").asInt()).isEqualTo(1);
        assertThat(report(id).path("content").get(1).path("errors").isEmpty()).isFalse();
        assertThat(report(id).path("content").get(1).path("row").asInt()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name",
                "objectArea",
                "entrance.latitude",
                "constructionAcceptancePlannedDate",
                "technicalSpecifications.buildingArea",
                "commercialTerms.turnoverPaymentPercent",
                "commercialTerms.counterpartyTaxStatus"
            })
    void invalidValuesProduceFieldAndReason(String field) throws Exception {
        var bad = row();
        bad.put(field, field.equals("name") ? "x".repeat(501) : "invalid");
        UUID id = enqueue(List.of(bad));
        imports.processNext();
        var errors = report(id).path("content").get(0).path("errors");
        assertThat(errors.toString()).contains(field);
        assertThat(state(id).path("rejected").asInt()).isEqualTo(1);
    }

    @Test
    void crossRegionInactiveAndNonManagerAssignmentsAreRejected() throws Exception {
        var cross = row();
        cross.put("managerId", outsider.toString());
        var role = row();
        role.put("managerId", lead.toString());
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        var inactive = row();
        inactive.put("managerId", colleague.toString());
        UUID id = enqueue(List.of(cross, role, inactive));
        imports.processNext();
        assertThat(state(id).path("rejected").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                .isZero();
    }

    @Test
    void duplicatesIncludingRefusalsAndRowsWithinFileAreCreatedAndMarked() throws Exception {
        add("Імпортований об’єкт", manager, region, "2026-01-15T12:00:00Z", "REFUSED");
        UUID id = enqueue(List.of(row(), row()));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(2);
        assertThat(state(id).path("duplicates").asInt()).isEqualTo(2);
        assertThat(report(id).path("content").get(0).path("note").asText()).isEqualTo("можливий дубль");
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='DUPLICATES_CONFIRMED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void identicalFileIsIdempotentAcrossConcurrentUploadsAndAdministrators() throws Exception {
        byte[] file = csv(List.of(row()));
        UUID secondAdmin = UUID.randomUUID();
        user(secondAdmin, "SUPERADMIN", null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> upload(admin, "first.csv", file)
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray());
            var second = executor.submit(() -> upload(secondAdmin, "renamed.csv", file)
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray());
            UUID id = UUID.fromString(json.readTree(first.get()).path("id").asText());
            assertThat(json.readTree(second.get()).path("id").asText()).isEqualTo(id.toString());
            imports.processNext();
            upload(admin, "retry.csv", file).andExpect(status().isAccepted());
            assertThat(imports.processNext()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                    .isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "CEO"})
    void everyNonAdminRoleIsDeniedOnAllImportEndpoints(String role) throws Exception {
        UUID actor = Map.of(
                        "MANAGER",
                        manager,
                        "REGIONAL_MANAGER",
                        lead,
                        "DEVELOPMENT_DIRECTOR",
                        director,
                        "CONTRACT_MANAGER",
                        contract,
                        "CEO",
                        ceo)
                .get(role);
        upload(actor, "objects.csv", csv(List.of(row()))).andExpect(status().isForbidden());
        for (String path : List.of(
                "/api/objects/import/template",
                "/api/objects/imports/" + UUID.randomUUID(),
                "/api/objects/imports/" + UUID.randomUUID() + "/report"))
            mvc.perform(get(path).cookie(cookie(actor))).andExpect(status().isForbidden());
    }

    @Test
    void rechecksAdministratorBeforeWorkerRuns() throws Exception {
        UUID id = enqueue(List.of(row()));
        jdbc.update("update users set role_id=(select id from roles where code='CEO') where id=?", admin);
        imports.processNext();
        jdbc.update("update users set role_id=(select id from roles where code='SUPERADMIN') where id=?", admin);
        assertThat(state(id).path("state").asText()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                .isZero();
        assertThat(report(id).path("content").get(0).path("state").asText()).isEqualTo("NOT_APPLIED");
    }

    @Test
    void wrongTemplateAndActualDatesAreRejectedWithoutQueueing() throws Exception {
        upload(admin, "objects.csv", "name,openingDate\r\nTest,2026-01-01".getBytes())
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from object_imports", Integer.class))
                .isZero();
    }

    @Test
    void failureHalfwayRollsBackObjectsHistoryContactsAndReportThenRetriesSafely() throws Exception {
        var row = row();
        row.putAll(Map.of("contact.name", "Контакт", "contact.role", "Власник", "contact.phone", "+380991234567"));
        UUID id = enqueue(List.of(row, row));
        jdbc.execute(
                "create function fail_second_import() returns trigger language plpgsql as $$ begin if (select count(*) from objects)>1 then raise exception 'test interruption'; end if; return new; end; $$");
        jdbc.execute(
                "create trigger fail_second_import after insert on objects for each row execute function fail_second_import()");
        try {
            imports.processNext();
            assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from object_history", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from object_contacts", Integer.class))
                    .isZero();
            assertThat(report(id).path("content").get(0).path("state").asText()).isEqualTo("PENDING");
        } finally {
            jdbc.execute("drop trigger fail_second_import on objects");
            jdbc.execute("drop function fail_second_import()");
        }
        jdbc.update("update object_imports set next_attempt_at=current_timestamp where id=?", id);
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from object_contacts", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void persistentFailureProducesReportForEveryUnappliedRow() throws Exception {
        when(geocoder.resolve(any(), any())).thenThrow(new IllegalStateException("test outage"));
        var name = row();
        name.remove("name");
        UUID id = enqueue(List.of(name));
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("update object_imports set next_attempt_at=current_timestamp where id=?", id);
            imports.processNext();
        }
        assertThat(state(id).path("state").asText()).isEqualTo("FAILED");
        assertThat(report(id).path("content").get(0).path("errors").toString()).contains("rolled back");
    }

    @Test
    void reportsArePaginatedAndUnknownJobIs404() throws Exception {
        UUID id = enqueue(List.of(row(), row(), row()));
        imports.processNext();
        var r = mvc.perform(get("/api/objects/imports/" + id + "/report")
                        .param("offset", "1")
                        .param("limit", "1")
                        .cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertThat(json.readTree(r.getContentAsByteArray()).path("content").size())
                .isEqualTo(1);
        mvc.perform(get("/api/objects/imports/" + id + "/report")
                        .param("limit", "201")
                        .cookie(cookie(admin)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects/imports/" + UUID.randomUUID()).cookie(cookie(admin)))
                .andExpect(status().isNotFound());
    }
}
