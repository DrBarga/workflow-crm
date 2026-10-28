package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test",
            "app.minio.secret-key=test-secret",
            "app.security.jwt.secret=object-api-test-secret-with-at-least-32-bytes",
            "app.security.jwt.issuer=object-test",
            "app.security.cors.allowed-origins=http://localhost:3000"
        })
class ObjectImportTemplateHttpIT extends ObjectImportTestSupport {
    @LocalServerPort
    int port;

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void downloadedTemplateImportsCalculatedDateAndPreservesOtherColumns(String format) throws Exception {
        var client = HttpClient.newHttpClient();
        var session = cookie(admin);
        String cookies = session.getName() + "=" + session.getValue() + "; " + csrf.getName() + "=" + csrf.getValue();
        var template = client.send(
                HttpRequest.newBuilder(uri("/api/objects/import/template?format=" + format))
                        .header("Cookie", cookies)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(template.statusCode()).isEqualTo(200);
        assertThat(template.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=objects-import-v2." + format);
        assertThat(template.headers().firstValue("Cache-Control")).contains("no-store");
        var values = row();
        values.put("constructionAcceptancePlannedDate", "2028-02-28");
        values.put("commercialTerms.constructionTermDays", "2");
        values.put("paymentActPlannedDate", "2028-03-04");
        values.put("technicalSpecifications.buildingArea", "345.67");
        values.put("commercialTerms.fixedRentAmount", "12345.67");
        values.put("shortComment", "Imported construction plan");
        byte[] file = fill(format, template.body(), values);
        String boundary = "import-" + UUID.randomUUID();
        var multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"objects."
                        + format + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        multipart.write(file);
        multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var accepted = client.send(
                HttpRequest.newBuilder(uri("/api/objects/import"))
                        .header("Cookie", cookies)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(count("objects")).isZero();
        String id = json.readTree(accepted.body()).path("id").asText();
        imports.processNext();
        var report = client.send(
                HttpRequest.newBuilder(uri("/api/objects/imports/" + id + "/report"))
                        .header("Cookie", cookies)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(report.statusCode()).isEqualTo(200);
        var item = json.readTree(report.body()).path("content").get(0);
        assertThat(item.path("state").asText()).isEqualTo("CREATED");
        assertThat(item.path("errors")).isEmpty();
        var object = jdbc.queryForMap(
                "select opening_planned_date::text as opening, payment_act_planned_date::text as payment,building_area,fixed_rent_amount,short_comment from objects");
        assertThat(object)
                .containsEntry("opening", "2028-03-01")
                .containsEntry("payment", "2028-03-04")
                .containsEntry("short_comment", "Imported construction plan");
        assertThat(object.get("building_area").toString()).isEqualTo("345.67");
        assertThat(object.get("fixed_rent_amount").toString()).isEqualTo("12345.67");
    }

    private byte[] fill(String format, byte[] template, Map<String, String> values) throws Exception {
        var columns = new ArrayList<String>();
        if (format.equals("csv")) {
            String text = new String(template, StandardCharsets.UTF_8).replaceFirst("^\ufeff", "");
            try (var parser = CSVFormat.RFC4180.parse(new StringReader(text))) {
                columns.addAll(parser.getRecords().getFirst().toList());
            }
            checkColumns(columns);
            var writer = new StringWriter();
            try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180)) {
                printer.printRecord(columns);
                printer.printRecord(
                        columns.stream().map(c -> values.getOrDefault(c, "")).toList());
            }
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template));
                var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(0).forEach(cell -> columns.add(cell.getStringCellValue()));
            checkColumns(columns);
            var row = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++)
                row.createCell(i).setCellValue(values.getOrDefault(columns.get(i), ""));
            int date = columns.indexOf("constructionAcceptancePlannedDate");
            row.getCell(date).setCellValue(java.time.LocalDate.of(2028, 2, 28));
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            row.getCell(date).setCellStyle(style);
            book.write(output);
            return output.toByteArray();
        }
    }

    private void checkColumns(List<String> columns) {
        assertThat(columns).doesNotContain("openingPlannedDate", "openingActualDate");
        assertThat(columns)
                .contains(
                        "constructionAcceptancePlannedDate",
                        "commercialTerms.constructionTermDays",
                        "technicalSpecifications.buildingArea",
                        "commercialTerms.fixedRentAmount",
                        "paymentActPlannedDate");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
