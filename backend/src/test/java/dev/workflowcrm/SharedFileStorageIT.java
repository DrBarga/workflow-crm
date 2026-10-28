package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.workflowcrm.files.api.*;
import dev.workflowcrm.integrations.api.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class SharedFileStorageIT extends ObjectFileTestSupport {
    @Autowired
    FileStorage files;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoSpyBean
    BinaryFileStorage binary;

    StoredFile store(FileOwner owner) throws Exception {
        return files.upload(owner, manager, category("OTHER"), "План.pdf", new ByteArrayInputStream(PDF), file -> {});
    }

    void assertMissing(String bucket, String key) {
        assertThatThrownBy(() -> minio.statObject(io.minio.StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build()))
                .isInstanceOf(io.minio.errors.ErrorResponseException.class)
                .satisfies(error -> assertThat(((io.minio.errors.ErrorResponseException) error)
                                .errorResponse()
                                .code())
                        .isEqualTo("NoSuchKey"));
    }

    @Test
    void objectAndTaskUseTheSameServiceWithSeparateOwnerNamespacesAndBuckets() throws Exception {
        UUID object = add("Shared files", manager, region);
        UUID task = task(object, "NEW");
        var objectFile = store(FileOwner.object(object));
        var taskFile = store(FileOwner.task(task));
        assertThat(objectFile.bucket()).isEqualTo(FILE_BUCKET);
        assertThat(taskFile.bucket()).isEqualTo(TASK_FILE_BUCKET);
        assertThat(objectFile.key()).isEqualTo("objects/" + object + "/" + objectFile.id());
        assertThat(taskFile.key()).isEqualTo("tasks/" + task + "/" + taskFile.id());
        assertThat(jdbc.queryForObject(
                        "select object_id is null and task_id=? from files where id=?",
                        Boolean.class,
                        task,
                        taskFile.id()))
                .isTrue();
        assertThat(taskFile.authorId()).isEqualTo(manager);
        assertThat(taskFile.contentType()).isEqualTo("application/pdf");
        assertThat(taskFile.sizeBytes()).isEqualTo(PDF.length);
        var link = files.download(FileOwner.task(task), taskFile.id());
        var client = HttpClient.newHttpClient();
        var response = client.send(
                HttpRequest.newBuilder(URI.create(link.url())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PDF);
        assertThat(client.send(
                                HttpRequest.newBuilder(URI.create(link.url().split("\\?")[0]))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(403);
        assertThatThrownBy(() -> files.download(FileOwner.object(object), taskFile.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error)
                                .getStatusCode()
                                .value())
                        .isEqualTo(404));
        Thread.sleep(3100);
        assertThat(client.send(
                                HttpRequest.newBuilder(URI.create(link.url()))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(403);
    }

    @Test
    void renamedFileIsRejectedBeforeTaskMetadataOrReservationIsCreated() throws Exception {
        UUID object = add("Task formats", manager, region);
        UUID task = task(object, "NEW");
        assertThatThrownBy(() -> files.upload(
                        FileOwner.task(task),
                        manager,
                        category("OTHER"),
                        "План.png",
                        new ByteArrayInputStream(PDF),
                        file -> {}))
                .isInstanceOf(dev.workflowcrm.shared.error.UnprocessableInputException.class);
        assertThat(count("files")).isZero();
        assertThat(count("file_upload_reservations")).isZero();
        verify(binary, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void storageFailureNeverPublishesTaskFileOrCallsItsHistoryCallback() throws Exception {
        UUID object = add("Task storage failure", manager, region);
        UUID task = task(object, "NEW");
        var published = new java.util.concurrent.atomic.AtomicBoolean();
        doThrow(new BinaryStorageException(new java.net.ConnectException("unavailable")))
                .when(binary)
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
        try {
            assertThatThrownBy(() -> files.upload(
                            FileOwner.task(task),
                            manager,
                            category("OTHER"),
                            "План.pdf",
                            new ByteArrayInputStream(PDF),
                            file -> published.set(true)))
                    .isInstanceOf(BinaryStorageException.class);
        } finally {
            reset(binary);
        }
        assertThat(published).isFalse();
        assertThat(count("files")).isZero();
        assertThat(count("file_upload_reservations")).isEqualTo(1);
        jdbc.update("update file_upload_reservations set next_attempt_at=clock_timestamp()");
        assertThat(cleanup.processNext()).isTrue();
        assertThat(count("file_upload_reservations")).isZero();
    }

    @Test
    void taskHistoryAndMetadataCommitOrRollBackTogetherAndOrphanBytesAreCleaned() throws Exception {
        UUID object = add("Task transaction", manager, region);
        UUID task = task(object, "NEW");
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    try {
                        files.upload(
                                FileOwner.task(task),
                                manager,
                                category("OTHER"),
                                "План.pdf",
                                new ByteArrayInputStream(PDF),
                                file -> {
                                    jdbc.update("update tasks set name='Changed' where id=?", task);
                                    throw new IllegalStateException("History failure");
                                });
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select name from tasks where id=?", String.class, task))
                .isEqualTo("Task");
        assertThat(count("files")).isZero();
        String key = jdbc.queryForObject("select storage_key from file_upload_reservations", String.class);
        assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                .bucket(TASK_FILE_BUCKET)
                                .object(key)
                                .build())
                        .size())
                .isEqualTo(PDF.length);
        jdbc.update("update file_upload_reservations set next_attempt_at=clock_timestamp()");
        assertThat(cleanup.processNext()).isTrue();
        assertMissing(TASK_FILE_BUCKET, key);
    }

    @Test
    void concurrentObjectAndTaskUploadsNeverOverwriteMatchingNames() throws Exception {
        UUID object = add("Concurrent owners", manager, region);
        UUID task = task(object, "NEW");
        var start = new CyclicBarrier(4);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<StoredFile>>();
            for (int i = 0; i < 4; i++) {
                var owner = i % 2 == 0 ? FileOwner.object(object) : FileOwner.task(task);
                futures.add(pool.submit(() -> {
                    start.await();
                    return store(owner);
                }));
            }
            var ids = new HashSet<UUID>();
            for (var future : futures) {
                var file = future.get(20, TimeUnit.SECONDS);
                ids.add(file.id());
                assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                        .bucket(file.bucket())
                                        .object(file.key())
                                        .build())
                                .size())
                        .isEqualTo(PDF.length);
            }
            assertThat(ids).hasSize(4);
        }
        assertThat(count("files")).isEqualTo(4);
        assertThat(count("file_upload_reservations")).isZero();
    }

    @Test
    void anonymousBucketPolicyBlocksUploadsAndNewLinksWithoutChangingThePolicy() throws Exception {
        UUID object = add("Private storage", manager, region);
        UUID task = task(object, "NEW");
        var file = store(FileOwner.task(task));
        String policy =
                "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                        + TASK_FILE_BUCKET + "/*\"]}]}";
        minio.setBucketPolicy(io.minio.SetBucketPolicyArgs.builder()
                .bucket(TASK_FILE_BUCKET)
                .config(policy)
                .build());
        try {
            assertThatThrownBy(() -> files.download(FileOwner.task(task), file.id()))
                    .isInstanceOf(BinaryStorageException.class);
            assertThatThrownBy(() -> store(FileOwner.task(task))).isInstanceOf(BinaryStorageException.class);
            assertThat(count("files")).isEqualTo(1);
            assertThat(minio.getBucketPolicy(io.minio.GetBucketPolicyArgs.builder()
                            .bucket(TASK_FILE_BUCKET)
                            .build()))
                    .contains("Allow");
        } finally {
            minio.deleteBucketPolicy(io.minio.DeleteBucketPolicyArgs.builder()
                    .bucket(TASK_FILE_BUCKET)
                    .build());
        }
    }

    @Test
    void physicalObjectDeletionIncludesFilesUploadedThroughTaskOwner() throws Exception {
        UUID object = add("Task cascade", manager, region);
        UUID task = task(object, "NEW");
        var stored = store(FileOwner.task(task));
        accepted(object);
        assertThat(processor.processNext()).isTrue();
        assertThat(count("files")).isZero();
        assertMissing(TASK_FILE_BUCKET, stored.key());
        var snapshot = json.readTree(jdbc.queryForObject(
                "select snapshot::text from object_deletion_audit where object_id=?", String.class, object));
        assertThat(snapshot.path("files").toString()).contains(stored.id().toString(), "tasks/" + task);
    }
}
