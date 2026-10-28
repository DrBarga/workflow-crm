package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectExportProcessor;
import io.minio.GetObjectArgs;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectExportTestSupport extends ObjectDeletionTestSupport {
    static final String EXPORT_BUCKET = "exports-it-" + UUID.randomUUID();

    @DynamicPropertySource
    static void exports(DynamicPropertyRegistry r) {
        r.add("app.objects.export.bucket", () -> EXPORT_BUCKET);
        r.add("app.objects.export.link-ttl", () -> "2s");
    }

    @Autowired
    ObjectExportProcessor exportProcessor;

    ResultActions export(UUID actor, Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/objects/export")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    UUID enqueue(UUID actor, String format, Map<String, String> filters) throws Exception {
        var body = new HashMap<>(filters);
        body.put("format", format);
        var response =
                export(actor, body).andExpect(status().isAccepted()).andReturn().getResponse();
        var value = json.readTree(response.getContentAsByteArray());
        UUID id = UUID.fromString(value.path("id").asText());
        assertThat(value.path("state").asText()).isEqualTo("QUEUED");
        assertThat(response.getHeader("Location")).isEqualTo("/api/objects/exports/" + id);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        return id;
    }

    ResultActions exportStatus(UUID actor, UUID id) throws Exception {
        return mvc.perform(get("/api/objects/exports/" + id).cookie(cookie(actor)));
    }

    JsonNode state(UUID actor, UUID id) throws Exception {
        return json.readTree(exportStatus(actor, id)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    ResultActions exportDownload(UUID actor, UUID id) throws Exception {
        return mvc.perform(get("/api/objects/exports/" + id + "/download").cookie(cookie(actor)));
    }

    byte[] content(UUID id) throws Exception {
        String key = jdbc.queryForObject("select storage_key from object_exports where id=?", String.class, id);
        try (var file = minio.getObject(
                GetObjectArgs.builder().bucket(EXPORT_BUCKET).object(key).build())) {
            return file.readAllBytes();
        }
    }

    String csv(UUID actor, Map<String, String> filters) throws Exception {
        UUID id = enqueue(actor, "CSV", filters);
        assertThat(exportProcessor.processNext()).isTrue();
        assertThat(state(actor, id).path("state").asText()).isEqualTo("READY");
        return new String(content(id), StandardCharsets.UTF_8);
    }

    UUID ready(UUID actor, String format) throws Exception {
        UUID id = enqueue(actor, format, Map.of());
        assertThat(exportProcessor.processNext()).isTrue();
        assertThat(state(actor, id).path("state").asText()).isEqualTo("READY");
        return id;
    }

    byte[] signedContent(UUID actor, UUID id) throws Exception {
        var response =
                exportDownload(actor, id).andExpect(status().isOk()).andReturn().getResponse();
        var link = json.readTree(response.getContentAsByteArray());
        var downloaded = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(link.path("url").asText()))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElse(""))
                .contains("attachment");
        return downloaded.body();
    }
}
