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
class ObjectCardHttpIT extends ObjectCardTestSupport {
    @LocalServerPort
    int port;

    @Test
    void realHttpPreservesRoleProjectionHeadersAndErrorStatuses() throws Exception {
        UUID object = add("HTTP об’єкт", manager, region);
        contractType(object);
        state(object, "CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update(
                "update tasks set status='WAITING_APPROVAL' where object_id=? and name='APPROVE_LOTUS_CONTRACT'",
                object);
        jdbc.update(
                "update objects set construction_acceptance_planned_date='2026-10-15',construction_acceptance_actual_date='2026-10-16',construction_term_days=2,preliminary_contract_signed_date='2026-09-01',commissioning_date='2026-10-14',short_comment='Коментар для повної картки' where id=?",
                object);
        var client = HttpClient.newHttpClient();
        var own = send(client, manager, object);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(json.readTree(own.body()).path("shortComment").asText()).isEqualTo("Коментар для повної картки");
        assertThat(own.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(json.readTree(own.body())
                        .path("workflow")
                        .path("contractType")
                        .asText())
                .isEqualTo("PRIMARY");
        assertThat(fields(json.readTree(send(client, colleague, object).body())))
                .containsExactlyInAnyOrder("name", "entrance");
        var technical = json.readTree(send(client, contract, object).body());
        assertThat(technical.has("address")).isFalse();
        assertThat(technical.has("commercialTerms")).isFalse();
        assertThat(technical.has("shortComment")).isFalse();
        assertThat(technical.path("openingPlannedDate").asText()).isEqualTo("2026-10-17");
        assertThat(technical.path("preliminaryContractSignedDate").asText()).isEqualTo("2026-09-01");
        assertThat(technical.path("commissioningDate").asText()).isEqualTo("2026-10-14");
        var signing = java.util.stream.StreamSupport.stream(
                        technical.path("availableTransitions").spliterator(), false)
                .filter(t -> t.path("code").asText().equals("SIGN_CONTRACT"))
                .findFirst()
                .orElseThrow();
        var incomplete = signing.path("unmetConditions").path("incompleteTasks");
        assertThat(incomplete).hasSize(1);
        assertThat(incomplete.get(0).path("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(incomplete.get(0).path("id").asText())
                .isEqualTo(jdbc.queryForObject(
                        "select id::text from tasks where object_id=? and name='APPROVE_LOTUS_CONTRACT'",
                        String.class,
                        object));
        assertThat(technical.path("contractType").asText()).isEqualTo("PRIMARY");
        assertThat(technical.path("constructionAcceptancePlannedDate").asText()).isEqualTo("2026-10-15");
        assertThat(technical.path("constructionAcceptanceActualDate").asText()).isEqualTo("2026-10-16");
        assertThat(json.readTree(send(client, admin, object).body())
                        .path("workflow")
                        .path("contractType")
                        .asText())
                .isEqualTo("PRIMARY");
        assertThat(send(client, manager, UUID.randomUUID()).statusCode()).isEqualTo(404);
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        assertThat(send(client, manager, object).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> send(HttpClient client, UUID actor, UUID object) throws Exception {
        var session = cookie(actor);
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/objects/" + object))
                        .header("Cookie", session.getName() + "=" + session.getValue())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
