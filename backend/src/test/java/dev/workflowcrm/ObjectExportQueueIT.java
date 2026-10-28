package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.integrations.api.BinaryStorageException;
import dev.workflowcrm.objects.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ObjectExportQueueIT extends ObjectExportTestSupport {
    @MockitoSpyBean
    ObjectExportFiles exportFiles;

    @MockitoSpyBean
    ObjectExportRenderer renderer;

    @MockitoSpyBean
    dev.workflowcrm.audit.api.ObjectExportAudit exportAudit;

    @MockitoSpyBean
    ObjectExportSettings settings;

    @Test
    void pendingDownloadIsConflictAndQueueIsBoundedPerUser() throws Exception {
        UUID id = enqueue(director, "CSV", Map.of());
        exportDownload(director, id).andExpect(status().isConflict());
        enqueue(director, "XLSX", Map.of());
        export(director, Map.of("format", "CSV")).andExpect(status().isTooManyRequests());
        assertThat(count("object_exports")).isEqualTo(2);
    }

    @Test
    void globalQueueRejectsWorkBeyondOneHundredPendingJobs() throws Exception {
        jdbc.update(
                """
                insert into object_exports(id,actor_id,format,filters,scope,requested_at,expires_at,bucket,storage_key)
                select gen_random_uuid(),gen_random_uuid(),'CSV','{}','{}',now(),now()+interval '1 day',
                'private','exports/test/'||n from generate_series(1,100) n
                """);
        export(director, Map.of("format", "CSV")).andExpect(status().isTooManyRequests());
        assertThat(count("object_exports")).isEqualTo(100);
    }

    @Test
    void fileSizeLimitDoesNotPublishPartialCsv() throws Exception {
        doReturn(16L).when(settings).maxFileBytes();
        UUID id = enqueue(director, "CSV", Map.of());
        exportProcessor.processNext();
        assertThat(state(director, id).path("state").asText()).isEqualTo("FAILED");
        assertThat(state(director, id).path("error").asText()).isEqualTo("EXPORT_LIMIT");
        verify(exportFiles, never()).upload(any(), any());
    }

    @Test
    void minioFailureRetriesWithoutReadyFileAndCompletesOnce() throws Exception {
        add("Офіс", manager, region);
        UUID id = enqueue(director, "CSV", Map.of());
        doThrow(new BinaryStorageException(new java.io.IOException()))
                .doCallRealMethod()
                .when(exportFiles)
                .upload(any(), any());
        assertThat(exportProcessor.processNext()).isTrue();
        assertThat(state(director, id).path("state").asText()).isEqualTo("QUEUED");
        assertThat(state(director, id).path("error").asText()).isEqualTo("STORAGE_UNAVAILABLE");
        exportDownload(director, id).andExpect(status().isConflict());
        assertThat(count("object_export_audit")).isEqualTo(1);
        jdbc.update("update object_exports set next_attempt_at=clock_timestamp() where id=?", id);
        assertThat(exportProcessor.processNext()).isTrue();
        assertThat(state(director, id).path("rows").asLong()).isEqualTo(1);
        assertThat(exportProcessor.processNext()).isFalse();
        assertThat(count("object_export_audit")).isEqualTo(2);
    }

    @Test
    void exhaustedRetriesFailClearly() throws Exception {
        UUID id = enqueue(director, "CSV", Map.of());
        doThrow(new BinaryStorageException(new java.io.IOException()))
                .when(exportFiles)
                .upload(any(), any());
        for (int i = 0; i < 3; i++) {
            jdbc.update("update object_exports set next_attempt_at=clock_timestamp() where id=?", id);
            exportProcessor.processNext();
        }
        assertThat(state(director, id).path("state").asText()).isEqualTo("FAILED");
        assertThat(state(director, id).path("error").asText()).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(exportProcessor.processNext()).isFalse();
    }

    @Test
    void competingWorkersSkipLockedJobWithoutDuplicateUploads() throws Exception {
        UUID id = enqueue(director, "CSV", Map.of());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
                    entered.countDown();
                    assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                    return call.callRealMethod();
                })
                .when(renderer)
                .render(any(), any(), anyLong());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(exportProcessor::processNext);
            try {
                assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
                assertThat(exportProcessor.processNext()).isFalse();
                assertThat(state(director, id).path("state").asText()).isEqualTo("QUEUED");
            } finally {
                release.countDown();
            }
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
        }
        verify(exportFiles, times(1)).upload(any(), any());
        assertThat(count("object_export_audit")).isEqualTo(2);
    }

    @Test
    void auditFailureRollsBackReadyAndRetryRemovesOrphanArtifact() throws Exception {
        UUID id = enqueue(director, "CSV", Map.of());
        doThrow(new IllegalStateException("audit unavailable"))
                .doCallRealMethod()
                .when(org.springframework.test.util.AopTestUtils
                        .<dev.workflowcrm.audit.api.ObjectExportAudit>getUltimateTargetObject(exportAudit))
                .record(eq(id), eq(director), eq("EXPORT_COMPLETED"), any());
        exportProcessor.processNext();
        assertThat(state(director, id).path("state").asText()).isEqualTo("QUEUED");
        assertThat(count("object_export_audit")).isEqualTo(1);
        assertThat(content(id)).isNotEmpty();
        jdbc.update("update object_exports set next_attempt_at=clock_timestamp() where id=?", id);
        exportProcessor.processNext();
        assertThat(state(director, id).path("state").asText()).isEqualTo("READY");
        assertThat(count("object_export_audit")).isEqualTo(2);
    }

    @Test
    void expiryPurgesArtifactButKeepsAudit() throws Exception {
        UUID id = ready(director, "CSV");
        jdbc.update("update object_exports set requested_at='2020-01-01',expires_at='2020-01-02' where id=?", id);
        exportDownload(director, id).andExpect(status().isGone());
        assertThat(state(director, id).path("state").asText()).isEqualTo("EXPIRED");
        assertThat(exportProcessor.cleanupNext()).isTrue();
        assertThat(exportProcessor.cleanupNext()).isFalse();
        assertThat(jdbc.queryForObject("select state from object_exports where id=?", String.class, id))
                .isEqualTo("EXPIRED");
        assertThat(count("object_export_audit")).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> content(id))
                .isInstanceOf(io.minio.errors.ErrorResponseException.class);
    }

    @Test
    void cellLimitFailsWithoutPublishingPartialXlsx() throws Exception {
        var object = add("Офіс", manager, region);
        jdbc.update("update objects set commercial_terms=? where id=?", "A".repeat(32768), object);
        UUID id = enqueue(director, "XLSX", Map.of());
        exportProcessor.processNext();
        assertThat(state(director, id).path("state").asText()).isEqualTo("FAILED");
        assertThat(state(director, id).path("error").asText()).isEqualTo("EXPORT_LIMIT");
        verify(exportFiles, never()).upload(any(), any());
        assertThat(csv(director, Map.of())).contains("A".repeat(32768));
    }
}
