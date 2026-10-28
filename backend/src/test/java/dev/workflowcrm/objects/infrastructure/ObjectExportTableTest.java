package dev.workflowcrm.objects.infrastructure;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.objects.domain.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ObjectExportTableTest {
    @Test
    void csvPreservesUnicodeEscapesQuotesAndProtectsOnlyTextFormulas() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var table = ObjectExportTable.open(ObjectExport.Format.CSV, output, List.of("name", "value"), 10000)) {
            table.row(List.of("Назва,\"Київ\"\nДалі", new BigDecimal("-30.5000000")));
            table.row(Arrays.asList("  =1+1", null));
            table.finish();
        }
        assertThat(output.toString(StandardCharsets.UTF_8))
                .isEqualTo(
                        "\ufeff\"name\",\"value\"\r\n\"Назва,\"\"Київ\"\"\nДалі\",\"-30.5000000\"\r\n\"'  =1+1\",\"\"\r\n");
    }

    @Test
    void csvRejectsOversizedCellsRatherThanTruncating() throws Exception {
        try (var table = ObjectExportTable.open(
                ObjectExport.Format.CSV, new ByteArrayOutputStream(), List.of("name"), 2000000)) {
            assertThatThrownBy(() -> table.row(List.of("A".repeat(1048577))))
                    .isInstanceOf(ObjectExportLimitException.class);
        }
    }

    @Test
    void xlsxPreservesLargeDecimalAsTextAndHasAHeaderForEmptyData() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var table = ObjectExportTable.open(ObjectExport.Format.XLSX, output, List.of("value"), 1000000)) {
            table.row(List.of(new BigDecimal("12345678901234567890.12")));
            table.finish();
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("12345678901234567890.12");
            assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("value");
        }
    }

    @Test
    void xlsxLimitsUncompressedInputAndCellLength() throws Exception {
        try (var table =
                ObjectExportTable.open(ObjectExport.Format.XLSX, new ByteArrayOutputStream(), List.of("name"), 10)) {
            assertThatThrownBy(() -> table.row(List.of("A".repeat(11)))).isInstanceOf(ObjectExportLimitException.class);
        }
        try (var table = ObjectExportTable.open(
                ObjectExport.Format.XLSX, new ByteArrayOutputStream(), List.of("name"), 1000000)) {
            assertThatThrownBy(() -> table.row(List.of("A".repeat(32768))))
                    .isInstanceOf(ObjectExportLimitException.class);
        }
    }

    @Test
    void adminColumnsExcludeContractDataAndOtherRolesKeepIt() {
        assertThat(ObjectExportColumns.forScope(new ObjectListScope(UUID.randomUUID(), "SUPERADMIN", List.of())))
                .extracting(ObjectExportColumns::name)
                .doesNotContain("contractType", "contractTypeId");
        assertThat(ObjectExportColumns.forScope(new ObjectListScope(UUID.randomUUID(), "CEO", List.of())))
                .extracting(ObjectExportColumns::name)
                .contains("contractType", "contractTypeId");
    }
}
