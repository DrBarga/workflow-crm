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
class ObjectReassignmentHttpIT extends ObjectReassignmentTestSupport {
    @LocalServerPort
    int port;

    @Test
    void realHttpReassignsTasksAndReturnsValidationAuthorizationAndConflictErrors() throws Exception {
        UUID object = add("HTTP об’єкт", manager, region);
        UUID task = task(object, manager, "PAUSED");
        assertThat(send(manager, object, colleague, 0).statusCode()).isEqualTo(403);
        assertThat(send(director, object, outsider, 0).statusCode()).isEqualTo(422);
        var success = send(lead, object, colleague, 0);
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(success.headers().firstValue("Cache-Control")).contains("no-store");
        var result = json.readTree(success.body());
        assertThat(result.path("managerId").asText()).isEqualTo(colleague.toString());
        assertThat(result.path("transferredTasks").get(0).path("label").asText())
                .isEqualTo("задача перейшла від попередника");
        assertThat(assignee(task)).isEqualTo(colleague);
        var stale = send(director, object, manager, 0);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(json.readTree(stale.body()).path("currentVersion").asLong()).isEqualTo(1);
        assertThat(owner(object)).isEqualTo(colleague);
    }

    private HttpResponse<String> send(UUID actor, UUID object, UUID manager, long version) throws Exception {
        var session = cookie(actor);
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + port + "/api/objects/" + object + "/reassign"))
                                .header(
                                        "Cookie",
                                        session.getName() + "=" + session.getValue() + "; " + csrf.getName() + "="
                                                + csrf.getValue())
                                .header("X-XSRF-TOKEN", csrfToken)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(
                                        json.writeValueAsString(Map.of("managerId", manager, "version", version))))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
