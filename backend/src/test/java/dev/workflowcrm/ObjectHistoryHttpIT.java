package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
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
class ObjectHistoryHttpIT extends ObjectHistoryTestSupport {
    @LocalServerPort
    int port;

    @Test
    void realHttpReturnsChronologyAndEnforcesAccessOnContinuation() throws Exception {
        UUID object = add("HTTP історія", manager, region);
        entry(object, manager, "name", "Було", "Стало");
        entry(object, null, "STATUS", "FOUND", "IN_PROGRESS");
        var client = HttpClient.newHttpClient();
        var first = send(client, manager, object, "?limit=1");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
        String cursor = json.readTree(first.body()).path("nextCursor").asText();
        var second = send(client, manager, object, "?cursor=" + cursor);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(json.readTree(second.body())
                        .path("items")
                        .get(0)
                        .path("author")
                        .path("name")
                        .asText())
                .isEqualTo("Система");
        assertThat(send(client, colleague, object, "?cursor=" + cursor).statusCode())
                .isEqualTo(403);
        assertThat(send(client, manager, object, "?limit=101").statusCode()).isEqualTo(422);
        assertThat(send(client, manager, UUID.randomUUID(), "").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> send(HttpClient client, UUID actor, UUID object, String query) throws Exception {
        var session = cookie(actor);
        return client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/objects/" + object + "/history" + query))
                        .header("Cookie", session.getName() + "=" + session.getValue())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
