package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

import dev.workflowcrm.objects.domain.ObjectExportRenderer;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.objects.deletion.worker-enabled=false", "spring.rabbitmq.host=localhost", "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test", "spring.rabbitmq.password=test",
                    "app.security.jwt.secret=object-api-test-secret-with-at-least-32-bytes",
            "app.security.jwt.issuer=object-test", "app.security.cors.allowed-origins=http://localhost:3000"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObjectExportLoadIT extends ObjectExportTestSupport {
    @LocalServerPort
    int port;

    @MockitoSpyBean
    ObjectExportRenderer renderer;

    @DynamicPropertySource
    static void worker(DynamicPropertyRegistry r) {
        r.add("app.objects.export.worker-enabled", () -> true);
        r.add("app.objects.export.poll-delay-ms", () -> 100);
    }

    @Test
    void fiftyThousandAndOneRowsExportInBothFormatsWhileHttpRemainsResponsive() throws Exception {
        ObjectListBenchmarkFixture.seed(jdbc, manager, colleague, outsider, region, otherRegion);
        add("Понад п’ятдесят тисяч", manager, region);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = new AtomicBoolean(true);
        doAnswer(call -> {
                    if (first.getAndSet(false)) {
                        entered.countDown();
                        if (!release.await(20, TimeUnit.SECONDS))
                            throw new IllegalStateException("Test release timed out");
                    }
                    return call.callRealMethod();
                })
                .when(renderer)
                .render(any(), any(), anyLong());
        var samples = new ArrayList<Double>();
        var client = HttpClient.newHttpClient();
        var reports = new ArrayList<Map<String, Object>>();
        for (String format : List.of("CSV", "XLSX")) {
            long start = System.nanoTime();
            UUID id = enqueue(director, format, Map.of());
            double acceptedMs = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(acceptedMs).isLessThan(3000);
            if (format.equals("CSV")) {
                try {
                    assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
                    samples.add(listResponse(client));
                    assertThat(state(director, id).path("state").asText()).isEqualTo("QUEUED");
                } finally {
                    release.countDown();
                }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (state(director, id).path("state").asText().equals("QUEUED") && System.nanoTime() < deadline) {
                samples.add(listResponse(client));
                Thread.sleep(100);
            }
            var ready = state(director, id);
            assertThat(ready.path("state").asText()).isEqualTo("READY");
            assertThat(ready.path("rows").asLong()).isEqualTo(50001);
            byte[] file = content(id);
            if (format.equals("CSV")) {
                try (var reader = new BufferedReader(new InputStreamReader(
                        new ByteArrayInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
                    assertThat(reader.lines().count()).isEqualTo(50002);
                }
            } else assertThat(xlsxRows(file)).isEqualTo(50002);
            reports.add(Map.of(
                    "format",
                    format,
                    "rows",
                    50001,
                    "acceptedMillis",
                    acceptedMs,
                    "readyMillis",
                    (System.nanoTime() - start) / 1_000_000.0,
                    "bytes",
                    file.length));
        }
        assertThat(count("object_export_audit")).isEqualTo(4);
        samples.sort(Comparator.naturalOrder());
        double p95 = samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
        assertThat(p95).isLessThan(2000);
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter()
                .writeValue(
                        Path.of("target/t5784-load.json").toFile(),
                        Map.of(
                                "objects",
                                50001,
                                "transport",
                                "HTTP localhost; PostgreSQL 17; Redis 7; MinIO",
                                "exports",
                                reports,
                                "concurrentListSamples",
                                samples.size(),
                                "concurrentListP95Millis",
                                p95));
    }

    private double listResponse(HttpClient client) throws Exception {
        var token = cookie(director);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/objects?limit=50"))
                .header("Cookie", token.getName() + "=" + token.getValue())
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        long start = System.nanoTime();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("content").size()).isEqualTo(50);
        return elapsed;
    }

    private long xlsxRows(byte[] content) throws Exception {
        Path file = Files.createTempFile("export-load-", ".xlsx");
        try {
            Files.write(file, content);
            try (var zip = new ZipFile(file.toFile())) {
                var entry = zip.getEntry("xl/worksheets/sheet1.xml");
                if (entry == null) throw new IllegalStateException("Missing XLSX worksheet");
                var count = new AtomicLong();
                var factory = javax.xml.parsers.SAXParserFactory.newInstance();
                factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                try (var sheet = zip.getInputStream(entry)) {
                    factory.newSAXParser().parse(sheet, new org.xml.sax.helpers.DefaultHandler() {
                        public void startElement(
                                String uri, String local, String name, org.xml.sax.Attributes attributes) {
                            if (name.equals("row")) count.incrementAndGet();
                        }
                    });
                }
                return count.get();
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
