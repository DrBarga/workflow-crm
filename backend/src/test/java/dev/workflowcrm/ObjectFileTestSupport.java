package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.files.domain.FileUploadCleanup;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectFileTestSupport extends ObjectDeletionTestSupport {
    static final String FILE_BUCKET = "files-it-" + UUID.randomUUID();
    static final String TASK_FILE_BUCKET = "tasks-it-" + UUID.randomUUID();
    static final byte[] PDF = "%PDF-1.7\nfile content".getBytes(StandardCharsets.US_ASCII);

    @DynamicPropertySource
    static void fileProperties(DynamicPropertyRegistry r) {
        r.add("app.files.bucket", () -> FILE_BUCKET);
        r.add("app.files.task-bucket", () -> TASK_FILE_BUCKET);
        r.add("app.files.cleanup-enabled", () -> false);
        r.add("app.files.link-ttl", () -> "2s");
    }

    @Autowired
    FileUploadCleanup cleanup;

    UUID category(String code) {
        return jdbc.queryForObject("select id from file_categories where code=?", UUID.class, code);
    }

    ResultActions upload(UUID actor, UUID object, UUID category, String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/objects/" + object + "/files")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .param("categoryId", category.toString())
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken));
    }

    JsonNode uploaded(UUID actor, UUID object, String code) throws Exception {
        return json.readTree(upload(actor, object, category(code), "Документ.pdf", PDF)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    UUID fileId(JsonNode file) {
        return UUID.fromString(file.path("id").asText());
    }

    ResultActions fileList(UUID actor, UUID object, Map<String, String> params) throws Exception {
        var r = get("/api/objects/" + object + "/files").cookie(cookie(actor));
        params.forEach(r::param);
        return mvc.perform(r);
    }

    JsonNode filePage(UUID actor, UUID object, Map<String, String> params) throws Exception {
        return json.readTree(fileList(actor, object, params)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    ResultActions link(UUID actor, UUID object, UUID file) throws Exception {
        return mvc.perform(get("/api/objects/" + object + "/files/" + file).cookie(cookie(actor)));
    }

    ResultActions deleteFile(UUID actor, UUID object, UUID file) throws Exception {
        return mvc.perform(delete("/api/objects/" + object + "/files/" + file)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken));
    }
}
