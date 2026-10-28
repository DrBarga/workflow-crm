package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
            "app.security.jwt.issuer=object-test",
            "app.security.cors.allowed-origins=http://localhost:3000"
        })
class ObjectMapPerformanceIT extends ObjectMapTestSupport {
    @LocalServerPort
    int port;

    record Measurement(
            String scenario,
            int pins,
            boolean truncated,
            int samples,
            double p50Millis,
            double p95Millis,
            double maxMillis,
            int bytes) {}

    @Test
    void twoThousandPinsInViewportStayBelowOneSecondAtP95OverHttp() throws Exception {
        ObjectListBenchmarkFixture.seed(jdbc, manager, colleague, outsider, region, otherRegion);
        jdbc.update(
                "update objects set entrance_longitude=40,entrance_latitude=48 where substring(name from 9)::int>2000");
        jdbc.update(
                """
                update objects set region_id=?,manager_id=case when substring(name from 9)::int%2=0 then ? else ? end,
                    status_id=(select id from object_statuses where code='FOUND'),
                    object_type_id=(select id from object_types where code='BUILT_IN_PREMISES'),created_at='2026-01-15T12:00:00Z'
                where substring(name from 9)::int<=2000
                """,
                region, colleague, manager);
        jdbc.execute("analyze objects");
        assertThat(count("objects")).isEqualTo(50000);
        var client = HttpClient.newHttpClient();
        var results = new ArrayList<Measurement>();
        var actors = new LinkedHashMap<String, UUID>();
        actors.put("director", director);
        actors.put("manager", manager);
        actors.put("regional", lead);
        actors.put("contract", contract);
        actors.put("ceo", ceo);
        actors.put("admin", admin);
        for (var actor : actors.entrySet())
            results.add(measure(client, actor.getValue(), actor.getKey(), Map.of("bbox", BOX), 2000, false));
        results.add(measure(
                client,
                director,
                "combined-filters",
                Map.of(
                        "bbox",
                        BOX,
                        "status",
                        "FOUND",
                        "regionId",
                        region.toString(),
                        "objectType",
                        "BUILT_IN_PREMISES",
                        "q",
                        "Магазин",
                        "createdFrom",
                        "2026-01-15",
                        "createdTo",
                        "2026-01-15"),
                2000,
                false));
        results.add(measure(client, director, "country-capped", Map.of("bbox", "22,44,41,53"), 2000, true));
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("objects", 50000);
        evidence.put("viewportObjects", 2000);
        evidence.put("transport", "HTTP localhost; embedded Tomcat; PostgreSQL 17; Redis 7");
        evidence.put("concurrency", 1);
        evidence.put("warmupPerScenario", 3);
        evidence.put("java", System.getProperty("java.version"));
        evidence.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        evidence.put("measurements", results);
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter()
                .writeValue(Path.of("target/t5783-performance.json").toFile(), evidence);
        for (var result : results)
            assertThat(result.p95Millis()).as(result.scenario()).isLessThan(1000);
    }

    private Measurement measure(
            HttpClient client,
            UUID actor,
            String scenario,
            Map<String, String> parameters,
            int expected,
            boolean truncated)
            throws Exception {
        String query = parameters.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        var cookie = cookie(actor);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/objects/map?" + query))
                .header("Cookie", cookie.getName() + "=" + cookie.getValue())
                .GET()
                .build();
        for (int i = 0; i < 3; i++) send(client, request, expected, truncated);
        var times = new ArrayList<Double>();
        int bytes = 0;
        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            times.add((System.nanoTime() - started) / 1_000_000.0);
            verify(response, expected, truncated);
            bytes = response.body().length;
        }
        times.sort(Comparator.naturalOrder());
        return new Measurement(scenario, expected, truncated, 20, times.get(9), times.get(18), times.get(19), bytes);
    }

    private void send(HttpClient client, HttpRequest request, int expected, boolean truncated) throws Exception {
        verify(client.send(request, HttpResponse.BodyHandlers.ofByteArray()), expected, truncated);
    }

    private void verify(HttpResponse<byte[]> response, int expected, boolean truncated) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        var result = json.readTree(response.body());
        assertThat(result.path("content")).hasSize(expected);
        assertThat(result.path("truncated").asBoolean()).isEqualTo(truncated);
    }
}
