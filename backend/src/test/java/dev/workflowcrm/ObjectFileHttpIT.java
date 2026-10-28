package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class ObjectFileHttpIT extends ObjectFileTestSupport {
    @Test
    void uploadStoresRealBytesAndReturnsServerMetadataAndAWorkingExpiringPrivateLink() throws Exception {
        UUID object = add("Metadata", manager, region);
        var response = upload(manager, object, category("MEDIA"), "План.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        var file = json.readTree(response.getContentAsByteArray());
        assertThat(fields(file))
                .containsExactlyInAnyOrder(
                        "id", "category", "author", "name", "contentType", "sizeBytes", "createdAt", "canDelete");
        assertThat(file.path("author").path("id").asText()).isEqualTo(manager.toString());
        assertThat(file.path("author").path("name").asText()).isEqualTo("Object Test User");
        assertThat(file.path("contentType").asText()).isEqualTo("application/pdf");
        assertThat(file.path("sizeBytes").asInt()).isEqualTo(PDF.length);
        assertThat(Instant.parse(file.path("createdAt").asText()))
                .isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(1));
        assertThat(response.getHeader("Location")).isEqualTo("/api/objects/" + object + "/files/" + fileId(file));
        var link = json.readTree(link(manager, object, fileId(file))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        String url = link.path("url").asText();
        try (var client = HttpClient.newHttpClient()) {
            var download = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo(PDF);
            assertThat(download.headers().firstValue("Content-Disposition").orElseThrow())
                    .contains("attachment");
            assertThat(download.headers().firstValue("Content-Type").orElseThrow())
                    .isEqualTo("application/octet-stream");
            String unsigned = url.substring(0, url.indexOf('?'));
            assertThat(client.send(
                                    HttpRequest.newBuilder(URI.create(unsigned))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding())
                            .statusCode())
                    .isEqualTo(403);
            Thread.sleep(3100);
            assertThat(client.send(
                                    HttpRequest.newBuilder(URI.create(url))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding())
                            .statusCode())
                    .isEqualTo(403);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "  ",
                "document.exe",
                "../document.pdf",
                "document.pdf\r\nX-Header: injected",
                "document.pdf "
            })
    void invalidNamesAndFormatsAre422(String name) throws Exception {
        UUID object = add("Name", manager, region);
        upload(manager, object, category("OTHER"), name, PDF)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("file"));
        assertThat(count("files")).isZero();
    }

    @Test
    void emptyDisguisedAndOverLimitFilesAreRejectedAndExactLimitIsAccepted() throws Exception {
        UUID object = add("Content", manager, region);
        upload(manager, object, category("OTHER"), "empty.pdf", new byte[0])
                .andExpect(status().isUnprocessableEntity());
        upload(manager, object, category("OTHER"), "fake.jpg", PDF).andExpect(status().isUnprocessableEntity());
        byte[] full = Arrays.copyOf(PDF, 20971520);
        upload(manager, object, category("OTHER"), "large.pdf", Arrays.copyOf(full, 20971521))
                .andExpect(status().isUnprocessableEntity());
        upload(manager, object, category("OTHER"), "exact.pdf", full).andExpect(status().isCreated());
    }

    @Test
    void inactiveMissingAndMalformedCategoryAndUnexpectedMetadataAre422() throws Exception {
        UUID object = add("Category", manager, region);
        UUID inactive = UUID.randomUUID();
        jdbc.update(
                "insert into file_categories(id,code,name,is_active) values(?,?, 'Inactive',false)",
                inactive,
                inactive.toString());
        for (UUID id : List.of(inactive, UUID.randomUUID()))
            upload(manager, object, id, "a.pdf", PDF).andExpect(status().isUnprocessableEntity());
        for (String id : List.of("bad", "1-1-1-1-1"))
            mvc.perform(multipart("/api/objects/" + object + "/files")
                            .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                            .param("categoryId", id)
                            .cookie(cookie(manager), csrf)
                            .header("X-XSRF-TOKEN", csrfToken))
                    .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/objects/" + object + "/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .param("categoryId", category("OTHER").toString())
                        .param("authorId", admin.toString())
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/objects/" + object + "/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/objects/" + object + "/files")
                        .param("categoryId", category("OTHER").toString())
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("file_upload_reservations")).isZero();
    }

    @Test
    void missingSessionCsrfForeignFileAndNonexistentObjectHaveDefinedResponses() throws Exception {
        UUID object = add("HTTP", manager, region), other = add("Other", manager, region);
        var file = uploaded(manager, object, "OTHER");
        mvc.perform(get("/api/objects/" + object + "/files")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/objects/" + object + "/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .param("categoryId", category("OTHER").toString())
                        .cookie(cookie(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/objects/" + object + "/files/" + fileId(file)).cookie(cookie(manager)))
                .andExpect(status().isForbidden());
        link(manager, other, fileId(file)).andExpect(status().isNotFound());
        deleteFile(manager, other, fileId(file)).andExpect(status().isNotFound());
        UUID missing = UUID.randomUUID();
        fileList(manager, missing, Map.of()).andExpect(status().isNotFound());
        upload(manager, missing, category("OTHER"), "a.pdf", PDF).andExpect(status().isNotFound());
        deleteFile(admin, missing, fileId(file)).andExpect(status().isNotFound());
    }

    @Test
    void softDeletePreservesCommentAndStoredBytesButHidesMetadataAndStopsIssuingLinks() throws Exception {
        UUID object = add("Comment reference", manager, region);
        var file = uploaded(manager, object, "OTHER");
        String reference = "Файл /api/objects/" + object + "/files/" + fileId(file);
        jdbc.update("insert into object_comments(object_id,author_id,body) values(?,?,?)", object, manager, reference);
        deleteFile(manager, object, fileId(file)).andExpect(status().isNoContent());
        assertThat(filePage(manager, object, Map.of()).path("items").size()).isZero();
        link(manager, object, fileId(file)).andExpect(status().isNotFound());
        deleteFile(manager, object, fileId(file)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select body from object_comments where object_id=?", String.class, object))
                .isEqualTo(reference);
        assertThat(jdbc.queryForObject(
                        "select deleted_at is not null from files where id=?", Boolean.class, fileId(file)))
                .isTrue();
        assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                .bucket(FILE_BUCKET)
                                .object("objects/" + object + "/" + fileId(file))
                                .build())
                        .size())
                .isEqualTo(PDF.length);
    }
}
