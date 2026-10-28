package dev.workflowcrm.objects.api;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ObjectImportDocumentsTest {
    private final ObjectImportDocuments documents = new ObjectImportDocuments();

    @Test
    void bothDownloadTemplatesRoundTripWithEmptySelection() throws IOException {
        for (String format : List.of("csv", "xlsx"))
            assertThat(documents.read("template." + format, documents.template(format)))
                    .isEmpty();
        assertThat(new String(documents.template("csv"), StandardCharsets.UTF_8))
                .contains(
                        "buildingArea", "fixedRentAmount", "constructionAcceptancePlannedDate", "paymentActPlannedDate")
                .doesNotContain("openingPlannedDate", "openingActualDate", "contractSignedDate");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(documents.template("xlsx")))) {
            var header = new ArrayList<String>();
            book.getSheetAt(0).getRow(0).forEach(cell -> header.add(cell.getStringCellValue()));
            assertThat(header)
                    .contains(
                            "technicalSpecifications.buildingArea",
                            "commercialTerms.constructionTermDays",
                            "constructionAcceptancePlannedDate",
                            "paymentActPlannedDate");
            assertThat(header)
                    .doesNotContain(
                            "openingPlannedDate",
                            "openingActualDate",
                            "constructionAcceptanceActualDate",
                            "paymentActActualDate");
        }
    }

    @Test
    void csvQuotedFieldsUnicodeBomAndLineNumbersArePreserved() throws Exception {
        var writer = new StringWriter();
        writer.write('\ufeff');
        var cells = new ArrayList<>(Collections.nCopies(ObjectImportTemplate.COLUMNS.size(), ""));
        cells.set(0, "Назва, \"лапки\"");
        cells.set(2, "Адреса");
        try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180)) {
            printer.printRecord(ObjectImportTemplate.COLUMNS);
            printer.printRecord(cells);
            printer.printRecord(cells);
        }
        var rows = documents.read("objects.csv", writer.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).row()).isEqualTo(2);
        assertThat(rows.get(1).row()).isEqualTo(3);
        assertThat(rows.get(0).values().get("name")).isEqualTo("Назва, \"лапки\"");
    }

    @Test
    void workbookNumericCellsDatesAndFormulaRejectionArePreserved() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(documents.template("xlsx")))) {
            var row = book.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("Назва");
            row.createCell(5).setCellValue(30.5234);
            row.createCell(8).setCellFormula("1+1");
            int dateColumn = ObjectImportTemplate.COLUMNS.indexOf("constructionAcceptancePlannedDate");
            row.createCell(dateColumn).setCellValue(java.time.LocalDate.of(2026, 12, 1));
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            row.getCell(dateColumn).setCellStyle(style);
            var output = new ByteArrayOutputStream();
            book.write(output);
            var imported = documents.read("objects.xlsx", output.toByteArray()).getFirst();
            assertThat(imported.values().get("entrance.longitude")).isEqualTo("30.5234");
            assertThat(imported.values().get("constructionAcceptancePlannedDate"))
                    .isEqualTo("2026-12-01");
            assertThat(imported.values().get("landArea")).contains("Unsupported cell type");
        }
    }

    @Test
    void rejectsUnknownHeaderExtraColumnsBrokenEncodingAndSpoofedExtension() {
        for (byte[] bytes : List.of(
                "wrong,header".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte) 0xc3, 0x28},
                "\"broken".getBytes(StandardCharsets.UTF_8)))
            assertThatThrownBy(() -> documents.read("objects.csv", bytes))
                    .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> documents.read("objects.xlsx", documents.template("csv")))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> documents.read("objects.csv", documents.template("xlsx")))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> documents.read("objects.pdf", documents.template("csv")))
                .isInstanceOf(UnprocessableInputException.class);
    }

    @Test
    void rejectsEmptyOversizedAndMoreThan10000Rows() throws Exception {
        assertThatThrownBy(() -> documents.read("objects.csv", new byte[0]))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> documents.read("objects.csv", new byte[20 * 1024 * 1024 + 1]))
                .isInstanceOf(UnprocessableInputException.class);
        var writer = new StringWriter();
        try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180)) {
            printer.printRecord(ObjectImportTemplate.COLUMNS);
            for (int i = 0; i < 10001; i++)
                printer.printRecord(Collections.nCopies(ObjectImportTemplate.COLUMNS.size(), ""));
        }
        assertThatThrownBy(() -> documents.read("objects.csv", writer.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnprocessableInputException.class);
    }

    @Test
    void blankRecordRemainsRowToValidate() {
        String header = new String(documents.template("csv"), StandardCharsets.UTF_8);
        assertThat(documents.read("objects.csv", (header + "\r\n").getBytes(StandardCharsets.UTF_8)))
                .hasSize(1);
    }

    @Test
    void corruptedWorkbookPackageIsAnInputError() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write("<broken>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThatThrownBy(() -> documents.read("objects.xlsx", output.toByteArray()))
                .isInstanceOf(UnprocessableInputException.class);
    }
}
