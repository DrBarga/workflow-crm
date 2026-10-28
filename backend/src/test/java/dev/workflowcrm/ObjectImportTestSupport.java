package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.api.ObjectImportTemplate;
import dev.workflowcrm.objects.domain.ObjectImportProcessor;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectImportTestSupport extends ObjectListTestSupport {
    @Autowired
    ObjectImportProcessor imports;

    @Autowired
    dev.workflowcrm.objects.domain.ObjectImportService importService;

    Map<String, String> row() {
        return new HashMap<>(Map.of(
                "name",
                "Імпортований об’єкт",
                "objectType",
                "BUILT_IN_PREMISES",
                "address",
                "Київ, адреса",
                "regionId",
                region.toString(),
                "managerId",
                manager.toString(),
                "entrance.longitude",
                "30.5234",
                "entrance.latitude",
                "50.4501",
                "objectArea",
                "120.5"));
    }

    byte[] csv(List<Map<String, String>> rows) throws Exception {
        var writer = new StringWriter();
        try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180)) {
            printer.printRecord(ObjectImportTemplate.COLUMNS);
            for (var row : rows) {
                if (!ObjectImportTemplate.COLUMNS.containsAll(row.keySet()))
                    throw new IllegalArgumentException("Test row contains columns outside the import template");
                printer.printRecord(ObjectImportTemplate.COLUMNS.stream()
                        .map(c -> row.getOrDefault(c, ""))
                        .toList());
            }
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    ResultActions upload(UUID actor, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/objects/import")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken));
    }

    UUID enqueue(List<Map<String, String>> rows) throws Exception {
        return UUID.fromString(json.readTree(upload(admin, "objects.csv", csv(rows))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("id")
                .asText());
    }

    JsonNode state(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/objects/imports/" + id).cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    JsonNode report(UUID id) throws Exception {
        return json.readTree(
                mvc.perform(get("/api/objects/imports/" + id + "/report").cookie(cookie(admin)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
    }
}
