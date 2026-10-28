package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.integrations.api.ReverseGeocoder;
import dev.workflowcrm.objects.api.ObjectImportTemplate;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ObjectImportReliabilityIT extends ObjectImportTestSupport {
    @Test
    void downloadableWorkbookCanBeFilledAndImported() throws Exception {
        var response = mvc.perform(get("/api/objects/import/template").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("objects-import-v2.xlsx");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var cells = book.getSheetAt(0).createRow(1);
            var row = row();
            for (int n = 0; n < ObjectImportTemplate.COLUMNS.size(); n++)
                cells.createCell(n).setCellValue(row.getOrDefault(ObjectImportTemplate.COLUMNS.get(n), ""));
            var output = new ByteArrayOutputStream();
            book.write(output);
            var result = upload(admin, "template.xlsx", output.toByteArray())
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse();
            UUID id = UUID.fromString(
                    json.readTree(result.getContentAsByteArray()).path("id").asText());
            imports.processNext();
            assertThat(state(id).path("created").asInt()).isEqualTo(1);
        }
        mvc.perform(get("/api/objects/import/template").param("format", "csv").cookie(cookie(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/objects/import/template").param("format", "pdf").cookie(cookie(admin)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void competingWorkersCannotApplySameJobTwice() throws Exception {
        var row = row();
        row.remove("name");
        UUID id = enqueue(List.of(row));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(geocoder.resolve(any(), any())).thenAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new ReverseGeocoder.Location("Київ", "UA-30", "Київ");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var processing = executor.submit(imports::processNext);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(imports.processNext()).isFalse();
            assertThat(report(id).path("content").get(0).path("state").asText()).isEqualTo("PENDING");
            release.countDown();
            assertThat(processing.get()).isTrue();
        } finally {
            release.countDown();
        }
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
    }

    @Test
    void completionAuditFailureRollsBackObjectsAndReport() throws Exception {
        UUID id = enqueue(List.of(row()));
        jdbc.execute(
                "create function fail_import_audit() returns trigger language plpgsql as $$ begin if new.event_type='IMPORT_COMPLETED' then raise exception 'test audit outage'; end if; return new; end; $$");
        jdbc.execute(
                "create trigger fail_import_audit before insert on object_import_audit for each row execute function fail_import_audit()");
        try {
            imports.processNext();
            assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                    .isZero();
            assertThat(report(id).path("content").get(0).path("state").asText()).isEqualTo("PENDING");
        } finally {
            jdbc.execute("drop trigger fail_import_audit on object_import_audit");
            jdbc.execute("drop function fail_import_audit()");
        }
        jdbc.update("update object_imports set next_attempt_at=current_timestamp where id=?", id);
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
    }

    @Test
    void limitsQueueAndAllowsHeaderOnlyFile() throws Exception {
        UUID empty = enqueue(List.of());
        imports.processNext();
        assertThat(state(empty).path("state").asText()).isEqualTo("COMPLETED");
        assertThat(report(empty).path("total").asInt()).isZero();
        for (int n = 0; n < 10; n++) {
            var row = row();
            row.put("name", "Import " + n);
            enqueue(List.of(row));
        }
        var row = row();
        row.put("name", "Overflow");
        upload(admin, "objects.csv", csv(List.of(row))).andExpect(status().isTooManyRequests());
    }

    @Test
    void unauthenticatedUploadAndCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/objects/import/template")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/objects/import")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "objects.csv", "text/csv", csv(List.of(row()))))
                        .cookie(cookie(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankRowAndOverlongTextHavePerRowErrors() throws Exception {
        var invalid = row();
        invalid.put("commercialTerms.leaseTerms", "x".repeat(5001));
        var extreme = row();
        extreme.put("objectArea", "1e2147483647");
        UUID id = enqueue(List.of(row(), Map.of(), invalid, extreme));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(state(id).path("rejected").asInt()).isEqualTo(3);
        assertThat(report(id).path("content").get(2).path("errors").toString()).contains("leaseTerms");
        assertThat(report(id).path("content").get(3).path("errors").toString())
                .contains("objectArea", "Invalid number");
    }
}
