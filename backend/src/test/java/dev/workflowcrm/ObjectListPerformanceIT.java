package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
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
class ObjectListPerformanceIT extends ObjectListTestSupport {
    @LocalServerPort
    int port;

    record Measurement(String scenario, int samples, double p50Millis, double p95Millis, double maxMillis, int bytes) {}

    @Test
    void filteredHttpPagesOfFiftyThousandObjectsStayBelowTwoSecondsAtP95() throws Exception {
        ObjectListBenchmarkFixture.seed(jdbc, manager, colleague, outsider, region, otherRegion);
        assertThat(count("objects")).isEqualTo(50000);
        var client = HttpClient.newHttpClient();
        var results = new ArrayList<Measurement>();
        for (String sort : List.of("createdAt", "name", "status", "investmentCommitteeDate", "contractSignedDate"))
            for (String direction : List.of("asc", "desc")) {
                results.add(measure(
                        client,
                        director,
                        sort + "-" + direction,
                        Map.of("sort", sort, "direction", direction, "limit", "50")));
            }
        results.add(measure(client, manager, "manager-pins", Map.of("limit", "50", "q", "Магазин")));
        results.add(measure(client, lead, "regional", Map.of("limit", "50", "regionId", region.toString())));
        results.add(measure(client, contract, "contract-technical", Map.of("limit", "50", "status", "FOUND")));
        results.add(measure(client, ceo, "combined", ObjectListBenchmarkFixture.combined(manager, region)));
        results.add(measure(
                client, lead, "foreign-region-pins", Map.of("limit", "50", "regionId", otherRegion.toString())));
        results.add(measure(client, lead, "regional-network-mixed", Map.of("limit", "50")));
        results.add(measure(client, lead, "regional-address-search", Map.of("limit", "50", "q", "Київська")));
        var params = new HashMap<>(Map.of("limit", "100"));
        for (int i = 0; i < 100; i++) {
            var response = send(client, director, params);
            params.put(
                    "cursor", json.readTree(response.body()).path("nextCursor").asText());
        }
        results.add(measure(client, director, "deep-cursor-after-10000", params));
        var report = new LinkedHashMap<String, Object>();
        report.put("objects", 50000);
        report.put("transport", "HTTP localhost; embedded Tomcat; PostgreSQL 17; Redis 7");
        report.put("concurrency", 1);
        report.put("warmupPerScenario", 3);
        report.put("java", System.getProperty("java.version"));
        report.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        report.put("measurements", results);
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter()
                .writeValue(Path.of("target/t5773-performance.json").toFile(), report);
        for (var r : results) assertThat(r.p95Millis()).as(r.scenario()).isLessThan(2000);
    }

    private Measurement measure(HttpClient client, UUID actor, String scenario, Map<String, String> parameters)
            throws Exception {
        for (int i = 0; i < 3; i++) send(client, actor, parameters);
        var durations = new ArrayList<Double>();
        int bytes = 0;
        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            var response = send(client, actor, parameters);
            durations.add((System.nanoTime() - started) / 1_000_000.0);
            bytes = response.body().length;
        }
        durations.sort(Comparator.naturalOrder());
        return new Measurement(scenario, 20, durations.get(9), durations.get(18), durations.get(19), bytes);
    }

    private HttpResponse<byte[]> send(HttpClient client, UUID actor, Map<String, String> parameters) throws Exception {
        String query = parameters.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        var cookie = cookie(actor);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/objects?" + query))
                .header("Cookie", cookie.getName() + "=" + cookie.getValue())
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }
}
