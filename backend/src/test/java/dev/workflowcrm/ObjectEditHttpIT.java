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
class ObjectEditHttpIT extends ObjectEditTestSupport {
    @LocalServerPort
    int port;

    @Test
    void realHttpValidatesPatchConflictAndPointApprovalCycle() throws Exception {
        UUID object = add("HTTP об’єкт", manager, region);
        var edit = send(manager, "PATCH", "/api/objects/" + object, Map.of("version", 0, "name", "HTTP зміна"));
        assertThat(edit.statusCode()).isEqualTo(200);
        assertThat(edit.headers().firstValue("Cache-Control")).contains("no-store");
        var conflict = send(lead, "PATCH", "/api/objects/" + object, Map.of("version", 0, "name", "Конкурентна зміна"));
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("currentVersion").asLong())
                .isEqualTo(1);
        assertThat(card(manager, object).path("name").asText()).isEqualTo("HTTP зміна");
        state(object, "IC_PREPARATION");
        var created = send(manager, "POST", "/api/objects/" + object + "/entry-point", coordinates());
        assertThat(created.statusCode()).isEqualTo(200);
        var pending = json.readTree(created.body());
        assertThat(pending.path("saved").asBoolean()).isFalse();
        String request = pending.path("approvalRequestId").asText();
        var approved = send(
                lead, "POST", "/api/objects/" + object + "/entry-point-requests/" + request + "/approve", Map.of());
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(json.readTree(approved.body()).path("saved").asBoolean()).isTrue();
        assertThat(version(object)).isEqualTo(2);
    }

    @Test
    void realHttpAppliesUpdatedFieldPermissionsAndCalculatedPlan() throws Exception {
        UUID object = add("HTTP field rules", manager, region);
        String path = "/api/objects/" + object;
        assertThat(send(manager, "PATCH", path, Map.of("version", 0, "commissioningDate", "2026-10-23"))
                        .statusCode())
                .isEqualTo(403);
        var patch = send(
                lead,
                "PATCH",
                path,
                Map.of(
                        "version",
                        0,
                        "commissioningDate",
                        "2026-10-23",
                        "preliminaryContractSignedDate",
                        "2026-10-20",
                        "shortComment",
                        "Ready for review",
                        "constructionAcceptancePlannedDate",
                        "2028-02-28",
                        "commercialTerms",
                        Map.of("constructionTermDays", 2),
                        "contractTypeId",
                        type()));
        assertThat(patch.statusCode()).isEqualTo(200);
        var read = send(lead, "GET", path, Map.of());
        assertThat(read.statusCode()).isEqualTo(200);
        var card = json.readTree(read.body());
        assertThat(card.path("shortComment").asText()).isEqualTo("Ready for review");
        assertThat(card.path("workflow")
                        .path("milestones")
                        .path("openingPlannedDate")
                        .asText())
                .isEqualTo("2028-03-01");
        assertThat(send(admin, "PATCH", path, Map.of("version", 1, "openingPlannedDate", "2030-01-01"))
                        .statusCode())
                .isEqualTo(422);
        ObjectContractTaskSetTestData.create(jdbc, object, type(), null, "CANCELLED");
        UUID other = jdbc.queryForObject("select id from contract_types where code='PRELIMINARY'", UUID.class);
        assertThat(send(lead, "PATCH", path, Map.of("version", 1, "contractTypeId", other))
                        .statusCode())
                .isEqualTo(422);
        assertThat(send(admin, "PATCH", path, Map.of("version", 1, "contractTypeId", other))
                        .statusCode())
                .isEqualTo(200);
    }

    private HttpResponse<String> send(UUID actor, String method, String path, Map<String, Object> body)
            throws Exception {
        var session = cookie(actor);
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .header(
                                        "Cookie",
                                        session.getName() + "=" + session.getValue() + "; " + csrf.getName() + "="
                                                + csrf.getValue())
                                .header("X-XSRF-TOKEN", csrfToken)
                                .header("Content-Type", "application/json")
                                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
