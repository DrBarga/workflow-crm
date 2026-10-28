package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.files.api.FileOwner;
import dev.workflowcrm.files.api.FileStorage;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectFileHeicIT extends ObjectFileTestSupport {
    @Autowired
    FileStorage files;

    @ParameterizedTest
    @ValueSource(strings = {"heic", "mif1"})
    void defaultSettingsAcceptHeicAndPreserveContentThroughSignedDownload(String majorBrand) throws Exception {
        UUID object = add("HEIC upload", manager, region);
        byte[] content = heic(majorBrand, "heic");
        var file = json.readTree(upload(manager, object, category("OTHER"), "Фото.HEIC", content)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/heic"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        UUID id = fileId(file);
        assertThat(minio.statObject(StatObjectArgs.builder()
                                .bucket(FILE_BUCKET)
                                .object("objects/" + object + "/" + id)
                                .build())
                        .contentType())
                .isEqualTo("image/heic");
        var signed = json.readTree(link(manager, object, id)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertDownload(signed.path("url").asText(), content);
    }

    @Test
    void taskOwnerUsesTheSameDefaultHeicValidationAndStorage() throws Exception {
        UUID object = add("Task HEIC upload", manager, region);
        UUID task = task(object, "NEW");
        byte[] content = heic("mif1", "heic");
        var owner = FileOwner.task(task);
        var stored = files.upload(
                owner, manager, category("OTHER"), "Фото.heic", new ByteArrayInputStream(content), file -> {});
        assertThat(stored.contentType()).isEqualTo("image/heic");
        assertThat(stored.bucket()).isEqualTo(TASK_FILE_BUCKET);
        assertDownload(files.download(owner, stored.id()).url(), content);
    }

    @Test
    void renamedAndTruncatedHeicAreRejectedWithoutMetadataOrUploadReservations() throws Exception {
        UUID object = add("Invalid HEIC", manager, region);
        for (byte[] content : new byte[][] {PDF, heic("avif", "mif1"), new byte[] {0, 0, 0, 20, 102, 116, 121, 112}}) {
            upload(manager, object, category("OTHER"), "Фото.heic", content)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("file"));
        }
        assertThat(count("files")).isZero();
        assertThat(count("file_upload_reservations")).isZero();
    }

    private byte[] heic(String majorBrand, String compatibleBrand) {
        return ByteBuffer.allocate(20)
                .putInt(20)
                .put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put(majorBrand.getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put(compatibleBrand.getBytes(StandardCharsets.US_ASCII))
                .array();
    }

    private void assertDownload(String url, byte[] content) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(content);
        }
    }
}
