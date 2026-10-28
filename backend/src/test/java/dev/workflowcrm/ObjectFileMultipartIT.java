package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.objects.deletion.worker-enabled=false",
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test",
            "app.minio.secret-key=test-secret",
            "app.security.jwt.secret=object-api-test-secret-with-at-least-32-bytes",
            "app.security.jwt.issuer=object-test"
        })
class ObjectFileMultipartIT extends ObjectFileTestSupport {
    @LocalServerPort
    int port;

    @Test
    void realServletAcceptsFilesAboveDefaultSpringLimitAndRejectsFilesAboveTwentyMiB() throws Exception {
        UUID object = add("HTTP multipart", manager, region);
        try (var client = HttpClient.newHttpClient()) {
            var accepted = client.send(
                    request(object, Arrays.copyOf(PDF, 2 * 1024 * 1024)), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(accepted.statusCode()).isEqualTo(201);
            assertThat(json.readTree(accepted.body()).path("sizeBytes").asLong())
                    .isEqualTo(2 * 1024 * 1024);
            var rejected =
                    client.send(request(object, Arrays.copyOf(PDF, 20971521)), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(rejected.statusCode()).isEqualTo(413);
            assertThat(json.readTree(rejected.body()).path("code").asText()).isEqualTo("FILE_TOO_LARGE");
            assertThat(count("files")).isEqualTo(1);
            assertThat(count("file_upload_reservations")).isZero();
        }
    }

    @Test
    void jsonInsteadOfMultipartIs415AndMalformedBodyDoesNotProduceServerError() throws Exception {
        UUID object = add("Wrong multipart", manager, region);
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    authenticated(object)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(415);
            var malformed = client.send(
                    authenticated(object)
                            .header("Content-Type", "multipart/form-data")
                            .POST(HttpRequest.BodyPublishers.ofString("bad body"))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(malformed.statusCode()).isEqualTo(400);
        }
    }

    private HttpRequest.Builder authenticated(UUID object) {
        var access = cookie(manager);
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/objects/" + object + "/files"))
                .header(
                        "Cookie",
                        access.getName() + "=" + access.getValue() + "; " + csrf.getName() + "=" + csrf.getValue())
                .header("X-XSRF-TOKEN", csrfToken);
    }

    private HttpRequest request(UUID object, byte[] content) throws Exception {
        String boundary = "file-it-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"categoryId\"\r\n\r\n"
                        + category("OTHER") + "\r\n--" + boundary
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"plan.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return authenticated(object)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
    }
}
