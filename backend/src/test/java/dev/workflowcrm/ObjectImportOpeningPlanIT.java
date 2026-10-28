package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.objects.api.ObjectImportTemplate;
import dev.workflowcrm.objects.domain.ObjectImport;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectImportOpeningPlanIT extends ObjectImportTestSupport {
    @ParameterizedTest
    @CsvSource({
        "2028-02-28,2,2028-03-01",
        "2026-12-31,2,2027-01-02",
        "2026-10-23,2,2026-10-25",
        "2026-10-23,0,2026-10-23"
    })
    void calculatesOpeningPlanFromImportedConstructionInputs(String acceptance, String days, String expected)
            throws Exception {
        var row = row();
        row.put("constructionAcceptancePlannedDate", acceptance);
        row.put("commercialTerms.constructionTermDays", days);
        UUID id = enqueue(List.of(row));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select opening_planned_date from objects", LocalDate.class))
                .isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "term", "both"})
    void missingConstructionInputLeavesOpeningPlanEmpty(String missing) throws Exception {
        var row = row();
        if (missing.equals("term")) row.put("constructionAcceptancePlannedDate", "2026-10-23");
        if (missing.equals("date")) row.put("commercialTerms.constructionTermDays", "0");
        UUID id = enqueue(List.of(row));
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select opening_planned_date from objects", LocalDate.class))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void rejectsUploadedOpeningPlanColumnBeforeQueueing(String format) throws Exception {
        var columns = new ArrayList<>(ObjectImportTemplate.COLUMNS);
        columns.add(columns.indexOf("constructionAcceptancePlannedDate") + 1, "openingPlannedDate");
        var row = row();
        row.put("openingPlannedDate", "2026-10-25");
        byte[] file;
        if (format.equals("csv")) {
            var writer = new StringWriter();
            try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180)) {
                printer.printRecord(columns);
                printer.printRecord(
                        columns.stream().map(c -> row.getOrDefault(c, "")).toList());
            }
            file = writer.toString().getBytes(StandardCharsets.UTF_8);
        } else {
            try (var book = new XSSFWorkbook();
                    var output = new ByteArrayOutputStream()) {
                var sheet = book.createSheet("Objects");
                var header = sheet.createRow(0);
                var data = sheet.createRow(1);
                for (int i = 0; i < columns.size(); i++) {
                    header.createCell(i).setCellValue(columns.get(i));
                    data.createCell(i).setCellValue(row.getOrDefault(columns.get(i), ""));
                }
                book.write(output);
                file = output.toByteArray();
            }
        }
        upload(admin, "objects." + format, file).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_imports")).isZero();
        assertThat(count("objects")).isZero();
    }

    @Test
    void alreadyQueuedLegacyRowWithMatchingOpeningPlanRemainsProcessable() throws Exception {
        var row = row();
        row.put("constructionAcceptancePlannedDate", "2026-10-23");
        row.put("commercialTerms.constructionTermDays", "2");
        row.put("openingPlannedDate", "2026-10-25");
        UUID id = importService
                .request(admin, "b".repeat(64), List.of(new ObjectImport.Input(2, row)))
                .id();
        imports.processNext();
        assertThat(state(id).path("created").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select opening_planned_date from objects", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 10, 25));
    }
}
