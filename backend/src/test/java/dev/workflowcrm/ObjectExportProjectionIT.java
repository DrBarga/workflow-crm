package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.objects.domain.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectExportProjectionIT extends ObjectExportTestSupport {
    @Autowired
    ObjectExportRenderer renderer;

    @Autowired
    PlatformTransactionManager transactions;

    @TempDir
    Path temporary;

    @Test
    void rendererKeepsForeignPinRestrictedIfGivenListScope() throws Exception {
        var own = add("Свій", manager, region);
        var foreign = add("Колега", colleague, region);
        var filter = new ObjectListFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ObjectListFilter.Sort.NAME,
                ObjectListFilter.Direction.ASC,
                50);
        var job = new ObjectExport.Job(
                UUID.randomUUID(),
                manager,
                ObjectExport.Format.CSV,
                filter,
                new ObjectListScope(manager, "MANAGER", List.of(region)),
                Instant.now(),
                Instant.now().plusSeconds(60),
                EXPORT_BUCKET,
                "test.csv",
                ObjectExport.State.QUEUED,
                0,
                null,
                0);
        Path file = temporary.resolve("projection.csv");
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        long rows = tx.execute(status -> {
            try {
                return renderer.render(job, file, 1000000);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertThat(rows).isEqualTo(2);
        var lines = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(lines.stream()
                        .filter(l -> l.contains(own.toString()))
                        .findFirst()
                        .orElseThrow())
                .contains("Свій", "FOUND", "9999.00", "Приватна адреса");
        assertThat(lines.stream()
                        .filter(l -> l.contains(foreign.toString()))
                        .findFirst()
                        .orElseThrow())
                .contains("Колега", "30.5000000", "50.4000000")
                .doesNotContain("FOUND", "9999.00", "Приватна адреса", "Технічний опис");
    }
}
