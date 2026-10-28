package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.integrations.api.BinaryStorage;
import dev.workflowcrm.integrations.api.BinaryStorageException;
import io.minio.*;
import io.minio.messages.VersioningConfiguration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ObjectDeletionStorageIT extends ObjectDeletionTestSupport {
    @MockitoSpyBean
    BinaryStorage binaries;

    @Test
    void removesAllVersionsAndDeleteMarkersOfObjectAndTaskFiles() throws Exception {
        UUID object = add("Files", manager, region), task = task(object, "DONE");
        String bucket = bucket();
        minio.setBucketVersioning(SetBucketVersioningArgs.builder()
                .bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null))
                .build());
        file(object, null, bucket, "object");
        put(bucket, "object");
        minio.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object("object").build());
        UUID softDeleted = file(null, task, bucket, "task");
        jdbc.update("update files set deleted_at=clock_timestamp() where id=?", softDeleted);
        put(bucket, "object-neighbour");
        accepted(object);
        processor.processNext();
        assertThat(stored(bucket)).isEqualTo(1);
        assertThat(minio.statObject(StatObjectArgs.builder()
                                .bucket(bucket)
                                .object("object-neighbour")
                                .build())
                        .size())
                .isEqualTo(7);
        assertThat(count("files")).isZero();
        assertThat(count("tasks")).isZero();
    }

    @Test
    void partialStorageFailureKeepsDatabaseAndRetryFinishesWithoutDuplicateAudit() throws Exception {
        UUID object = add("Retry", manager, region), task = task(object, "NEW");
        String bucket = bucket();
        file(object, null, bucket, "first");
        file(null, task, bucket, "second");
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    if (calls.incrementAndGet() == 1)
                        throw new BinaryStorageException(new java.io.IOException("unavailable"));
                    return null;
                })
                .when(binaries)
                .deleteAllVersions(anyString(), anyString());
        UUID operation = UUID.fromString(accepted(object).path("id").asText());
        assertThat(processor.processNext()).isTrue();
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("files")).isEqualTo(2);
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(count("object_deletion_audit")).isZero();
        assertThat(stored(bucket)).isEqualTo(1);
        operation(admin, operation)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastError").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.state").value("PENDING"));
        assertThat(processor.processNext()).isFalse();
        reset(binaries);
        retry(operation);
        assertThat(processor.processNext()).isTrue();
        assertThat(stored(bucket)).isZero();
        assertThat(count("objects")).isZero();
        assertThat(count("files")).isZero();
        assertThat(count("object_deletion_audit")).isEqualTo(1);
    }

    @Test
    void databaseFailureAfterStoragePurgeIsSafelyRetried() throws Exception {
        UUID object = add("Commit retry", manager, region);
        String bucket = bucket();
        file(object, null, bucket, "file");
        UUID operation = UUID.fromString(accepted(object).path("id").asText());
        jdbc.execute("alter table object_deletion_audit add constraint test_snapshot_failure check(false) not valid");
        try {
            processor.processNext();
            assertThat(stored(bucket)).isZero();
            assertThat(count("objects")).isEqualTo(1);
            assertThat(count("files")).isEqualTo(1);
            assertThat(count("object_deletion_audit")).isZero();
            operation(admin, operation)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastError").value("DATABASE_FAILURE"));
        } finally {
            jdbc.execute("alter table object_deletion_audit drop constraint test_snapshot_failure");
        }
        retry(operation);
        processor.processNext();
        assertThat(count("objects")).isZero();
        assertThat(count("object_deletion_audit")).isEqualTo(1);
    }

    @Test
    void competingWorkersCannotPurgeTheSameOperationConcurrently() throws Exception {
        UUID object = add("Worker claim", manager, region);
        String bucket = bucket();
        file(object, null, bucket, "file");
        accepted(object);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Worker did not release");
                    return invocation.callRealMethod();
                })
                .when(binaries)
                .deleteAllVersions(anyString(), anyString());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(processor::processNext);
            try {
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
                assertThat(accepted(object).path("state").asText()).isEqualTo("PENDING");
                assertThat(pool.submit(processor::processNext).get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isFalse();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        verify(binaries, times(1)).deleteAllVersions(anyString(), anyString());
        assertThat(count("object_deletion_audit")).isEqualTo(1);
    }

    @Test
    void filesCannotBypassPhysicalPurgeWithDirectDatabaseDelete() throws Exception {
        UUID object = add("Protected storage", manager, region);
        String bucket = bucket();
        file(object, null, bucket, "file");
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    jdbc.queryForObject(
                            "select set_config('workflow.object_deletion_actor',?,true)",
                            String.class,
                            admin.toString());
                    jdbc.update("delete from objects where id=?", object);
                }))
                .isInstanceOf(DataAccessException.class);
        assertThat(count("objects")).isEqualTo(1);
        assertThat(stored(bucket)).isEqualTo(1);
    }
}
