package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DirtiesContext
class ObjectImportLoadIT extends ObjectImportTestSupport {
    @Autowired
    @Qualifier("objectImportExecutor")
    ThreadPoolTaskExecutor worker;

    @DynamicPropertySource
    static void worker(DynamicPropertyRegistry r) {
        r.add("app.objects.import.worker-enabled", () -> "true");
        r.add("app.objects.import.poll-delay-ms", () -> "100");
    }

    @Test
    void realWorkerImports10000RowsWithoutBlockingReadRequests() throws Exception {
        var rows = new ArrayList<Map<String, String>>();
        for (int n = 0; n < 10000; n++) {
            var row = row();
            row.put("name", UUID.randomUUID().toString());
            row.put("address", "Адреса " + UUID.randomUUID());
            row.put("constructionAcceptancePlannedDate", "2026-12-31");
            row.put("commercialTerms.constructionTermDays", "2");
            row.put(
                    "entrance.longitude",
                    BigDecimal.valueOf(30 + n % 100 * 0.01)
                            .setScale(2, java.math.RoundingMode.HALF_UP)
                            .toString());
            row.put(
                    "entrance.latitude",
                    BigDecimal.valueOf(48.5 + n / 100 * 0.01)
                            .setScale(2, java.math.RoundingMode.HALF_UP)
                            .toString());
            rows.add(row);
        }
        try {
            long start = System.nanoTime();
            UUID id = enqueue(rows);
            long acceptedMs = (System.nanoTime() - start) / 1000000;
            assertThat(acceptedMs).isLessThan(10000);
            var latencies = new ArrayList<Long>();
            var deadline = Instant.now().plus(Duration.ofMinutes(12));
            while (Instant.now().isBefore(deadline)
                    && state(id).path("state").asText().equals("QUEUED")) {
                long read = System.nanoTime();
                request(admin, Map.of("limit", "1")).andExpect(status().isOk());
                latencies.add((System.nanoTime() - read) / 1000000);
                Thread.sleep(200);
            }
            var result = state(id);
            assertThat(result.path("state").asText()).isEqualTo("COMPLETED");
            assertThat(result.path("created").asInt()).isEqualTo(10000);
            assertThat(result.path("rejected").asInt()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from objects", Integer.class))
                    .isEqualTo(10000);
            assertThat(jdbc.queryForObject(
                            "select count(*) from objects where opening_planned_date='2027-01-02'", Integer.class))
                    .isEqualTo(10000);
            var last = json.readTree(mvc.perform(get("/api/objects/imports/" + id + "/report")
                            .param("offset", "9999")
                            .param("limit", "1")
                            .cookie(cookie(admin)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray());
            assertThat(last.path("content").get(0).path("row").asInt()).isEqualTo(10001);
            assertThat(last.path("content").get(0).path("state").asText()).isEqualTo("CREATED");
            Collections.sort(latencies);
            long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
            assertThat(p95).isLessThan(1000);
            Path evidence = Path.of("target", "t5785-evidence");
            Files.createDirectories(evidence);
            Files.writeString(
                    evidence.resolve("load.json"),
                    json.writeValueAsString(Map.of(
                            "rows",
                            10000,
                            "acceptedMs",
                            acceptedMs,
                            "readyMs",
                            (System.nanoTime() - start) / 1000000,
                            "readSamples",
                            latencies.size(),
                            "readP95Ms",
                            p95)));
        } finally {
            worker.getThreadPoolExecutor().shutdownNow();
        }
    }
}
