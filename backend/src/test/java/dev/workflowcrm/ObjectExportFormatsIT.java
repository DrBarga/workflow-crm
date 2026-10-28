package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ObjectExportFormatsIT extends ObjectExportTestSupport {
    @Test
    void csvContainsTechnicalCommercialAndEveryMilestoneWithUnicodeEscaping() throws Exception {
        UUID object = add("Об’єкт, \"Київ\"\nДруга назва", manager, region);
        jdbc.update(
                """
                update objects set lease_terms='Оренда',rent_indexation_percent=5.5,turnover_payment_percent=2,
                counterparty_tax_status='COMPANY_VAT',construction_term_days=90,contract_duration_years=5,
                first_floor_area=80,second_floor_area=20,basement_area=10,sales_hall_area=70,floor_slab_height=3.5,
                water_supply_flow=15,power_connection_category='II',sewage_type='CENTRALIZED',
                contract_type_id=(select id from contract_types where code='PRIMARY'),
                contract_signed_date='2026-02-01',construction_acceptance_planned_date='2026-03-01',
                construction_acceptance_actual_date='2026-03-02',opening_planned_date='2026-04-01',opening_date='2026-04-02',
                payment_act_planned_date='2026-05-01',payment_act_actual_date='2026-05-02' where id=?
                """,
                object);
        String csv = csv(director, Map.of());
        assertThat(csv).startsWith("\ufeff\"id\",\"name\"").contains("Об’єкт, \"\"Київ\"\"\nДруга назва");
        assertThat(csv)
                .contains(
                        "technicalSpecifications.connectedPower",
                        "commercialTerms.fixedRentAmount",
                        "constructionAcceptancePlannedDate",
                        "constructionAcceptanceActualDate",
                        "openingPlannedDate",
                        "openingActualDate",
                        "paymentActPlannedDate",
                        "paymentActActualDate",
                        "contractSignedDate",
                        "\"Оренда\"",
                        "\"9999.00\"",
                        "\"COMPANY_VAT\"",
                        "\"PRIMARY\"",
                        "2026-05-02");
        assertThat(csv).doesNotContain("+380991234567", "Контакт", "contactPhone");
    }

    @Test
    void xlsxIsReadableWithNumbersStringsAndNoFormulasOrHyperlinks() throws Exception {
        add("=HYPERLINK(\"https://example.invalid\",\"назва\")", manager, region);
        UUID id = ready(director, "XLSX");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(signedContent(director, id)))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            var heading = sheet.getRow(0);
            assertThat(heading.getCell(1).getStringCellValue()).isEqualTo("name");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).startsWith("=HYPERLINK");
            for (var cell : sheet.getRow(1)) {
                assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);
                assertThat(cell.getHyperlink()).isNull();
            }
            int rent = -1;
            for (var cell : heading)
                if (cell.getStringCellValue().equals("commercialTerms.fixedRentAmount")) rent = cell.getColumnIndex();
            assertThat(sheet.getRow(1).getCell(rent).getNumericCellValue()).isEqualTo(9999);
        }
    }

    @Test
    void emptySelectionProducesHeadersInBothFormats() throws Exception {
        String csv = csv(director, Map.of());
        assertThat(csv.lines().count()).isEqualTo(1);
        UUID id = ready(director, "XLSX");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(content(id)))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isZero();
            assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isGreaterThan((short) 45);
        }
    }

    @Test
    void csvNeutralizesSpreadsheetFormulasAfterWhitespace() throws Exception {
        for (String name : List.of("=1+1", "+SUM(1,2)", "-2+3", "@SUM(1)", " \t=1+1")) add(name, manager, region);
        String csv = csv(director, Map.of());
        assertThat(csv).contains("\"'=1+1\"", "\"'+SUM(1,2)\"", "\"'-2+3\"", "\"'@SUM(1)\"", "\"' \t=1+1\"");
    }

    @Test
    void adminExportOmitsContractTypeColumnsLikeTheCard() throws Exception {
        add("Приміщення", manager, region);
        String csv = csv(admin, Map.of());
        assertThat(csv).doesNotContain("\"contractType\"", "\"contractTypeId\"");
        assertThat(csv).contains("commercialTerms.fixedRentAmount", "contractSignedDate", "Приміщення");
    }

    @Test
    void signedUrlExpiresAndAnonymousObjectReadIsDenied() throws Exception {
        add("Приміщення", manager, region);
        UUID id = ready(director, "CSV");
        var link = json.readTree(
                exportDownload(director, id).andReturn().getResponse().getContentAsByteArray());
        var client = java.net.http.HttpClient.newHttpClient();
        URI signed = java.net.URI.create(link.path("url").asText());
        var plain = java.net.URI.create(signed.getScheme() + "://" + signed.getAuthority() + signed.getPath());
        assertThat(client.send(
                                java.net.http.HttpRequest.newBuilder(plain)
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(403);
        Thread.sleep(2300);
        assertThat(client.send(
                                java.net.http.HttpRequest.newBuilder(signed)
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(403);
    }
}
