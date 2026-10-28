package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.integrations.api.BinaryFileStorage;
import dev.workflowcrm.integrations.api.BinaryStorageException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ObjectFileTransactionIT extends ObjectFileTestSupport {
    @MockitoSpyBean
    BinaryFileStorage storage;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    dev.workflowcrm.integrations.api.BinaryStorage purgeStorage;

    @Test
    void failedCleanupIsRetriedWithoutRemovingTheReservation() throws Exception {
        UUID reservation = UUID.randomUUID();
        String key = "objects/" + reservation;
        if (!minio.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(FILE_BUCKET).build()))
            minio.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(FILE_BUCKET).build());
        put(FILE_BUCKET, key);
        jdbc.update(
                "insert into file_upload_reservations(id,bucket,storage_key,next_attempt_at) values(?,?,?,clock_timestamp())",
                reservation,
                FILE_BUCKET,
                key);
        doThrow(new BinaryStorageException(new java.io.IOException("unavailable")))
                .when(purgeStorage)
                .deleteAllVersions(FILE_BUCKET, key);
        try {
            assertThat(cleanup.processNext()).isTrue();
            assertThat(jdbc.queryForObject(
                            "select attempts from file_upload_reservations where id=?", Integer.class, reservation))
                    .isEqualTo(1);
            assertThat(cleanup.processNext()).isFalse();
        } finally {
            reset(purgeStorage);
        }
        expire();
        assertThat(cleanup.processNext()).isTrue();
        assertThat(count("file_upload_reservations")).isZero();
    }

    @Test
    void cleanupDoesNotPurgeAnAttachedKeyOrAReservationLockedByAnActiveUpload() throws Exception {
        UUID object = add("Cleanup safety", manager, region);
        var file = uploaded(manager, object, "OTHER");
        jdbc.update(
                "insert into file_upload_reservations(id,bucket,storage_key,next_attempt_at) values(?,?,?,clock_timestamp())",
                UUID.randomUUID(),
                FILE_BUCKET,
                "objects/" + object + "/" + fileId(file));
        assertThat(cleanup.processNext()).isFalse();
        assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                .bucket(FILE_BUCKET)
                                .object("objects/" + object + "/" + fileId(file))
                                .build())
                        .size())
                .isEqualTo(PDF.length);
        UUID pending = UUID.randomUUID();
        jdbc.update(
                "insert into file_upload_reservations(id,bucket,storage_key,next_attempt_at) values(?,?,?,clock_timestamp())",
                pending,
                FILE_BUCKET,
                "objects/" + pending);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        try (var pool = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select id from file_upload_reservations where id=? for update", UUID.class, pending);
                try {
                    assertThat(pool.submit(cleanup::processNext).get(10, TimeUnit.SECONDS))
                            .isFalse();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
        assertThat(cleanup.processNext()).isTrue();
        assertThat(count("file_upload_reservations")).isEqualTo(1);
    }

    @Test
    void parallelUploadsUseIndependentKeysAndPreserveEveryFileAndHistoryEntry() throws Exception {
        UUID object = add("Concurrent files", manager, region);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var start = new CyclicBarrier(4);
            var results = new ArrayList<Future<UUID>>();
            for (int i = 0; i < 4; i++)
                results.add(pool.submit(() -> {
                    start.await();
                    return fileId(uploaded(manager, object, "OTHER"));
                }));
            var ids = new HashSet<UUID>();
            for (var result : results) ids.add(result.get(40, TimeUnit.SECONDS));
            assertThat(ids).hasSize(4);
            assertThat(filePage(manager, object, Map.of()).path("items").size()).isEqualTo(4);
            for (UUID id : ids)
                assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                        .bucket(FILE_BUCKET)
                                        .object("objects/" + object + "/" + id)
                                        .build())
                                .size())
                        .isEqualTo(PDF.length);
        }
        assertThat(count("object_history")).isEqualTo(4);
        assertThat(count("file_upload_reservations")).isZero();
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isZero();
    }

    @Test
    void historyFailureRollsBackUploadAndLeavesDurableCleanupThatRemovesStoredBytes() throws Exception {
        UUID object = add("Upload rollback", manager, region);
        jdbc.execute(
                "alter table object_history add constraint test_file_history check(field_name not like 'FILE_%') not valid");
        try {
            upload(manager, object, category("OTHER"), "a.pdf", PDF).andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("alter table object_history drop constraint test_file_history");
        }
        assertThat(count("files")).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("file_upload_reservations")).isEqualTo(1);
        UUID id = jdbc.queryForObject("select id from file_upload_reservations", UUID.class);
        assertThat(minio.statObject(io.minio.StatObjectArgs.builder()
                                .bucket(FILE_BUCKET)
                                .object("objects/" + object + "/" + id)
                                .build())
                        .size())
                .isEqualTo(PDF.length);
        expire();
        assertThat(cleanup.processNext()).isTrue();
        assertThat(count("file_upload_reservations")).isZero();
        assertThatThrownBy(() -> minio.statObject(io.minio.StatObjectArgs.builder()
                        .bucket(FILE_BUCKET)
                        .object("objects/" + object + "/" + id)
                        .build()))
                .isInstanceOf(io.minio.errors.ErrorResponseException.class);
    }

    @Test
    void storageFailureIs503AndRetryCleanupHandlesAnUploadThatStoredBytesBeforeLosingResponse() throws Exception {
        UUID object = add("Storage response lost", manager, region);
        doAnswer(call -> {
                    call.callRealMethod();
                    throw new BinaryStorageException(new java.io.IOException("response lost"));
                })
                .when(storage)
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
        try {
            upload(manager, object, category("OTHER"), "a.pdf", PDF).andExpect(status().isServiceUnavailable());
        } finally {
            reset(storage);
        }
        assertThat(count("files")).isZero();
        assertThat(count("object_history")).isZero();
        expire();
        cleanup.processNext();
        assertThat(count("file_upload_reservations")).isZero();
        uploaded(manager, object, "OTHER");
    }

    @Test
    void deleteAndHistoryAreAtomicAndDeletionDoesNotChangeObjectVersion() throws Exception {
        UUID object = add("Delete rollback", manager, region);
        var file = uploaded(manager, object, "OTHER");
        jdbc.execute(
                "alter table object_history add constraint test_file_delete_history check(field_name <> 'FILE_DELETED') not valid");
        try {
            deleteFile(manager, object, fileId(file)).andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("alter table object_history drop constraint test_file_delete_history");
        }
        assertThat(filePage(manager, object, Map.of()).path("items").size()).isEqualTo(1);
        deleteFile(manager, object, fileId(file)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList(
                        "select field_name from object_history where object_id=? order by sequence",
                        String.class,
                        object))
                .containsExactly("FILE_UPLOADED", "FILE_DELETED");
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isZero();
    }

    @Test
    void pendingObjectDeletionFreezesFilesAndCompletionPurgesEvenSoftDeletedFiles() throws Exception {
        UUID object = add("Physical deletion", manager, region);
        var file = uploaded(manager, object, "OTHER");
        var deleted = uploaded(contract, object, "CONTRACTS");
        deleteFile(contract, object, fileId(deleted)).andExpect(status().isNoContent());
        accepted(object);
        fileList(manager, object, Map.of()).andExpect(status().isConflict());
        link(manager, object, fileId(file)).andExpect(status().isConflict());
        upload(manager, object, category("OTHER"), "a.pdf", PDF).andExpect(status().isConflict());
        deleteFile(admin, object, fileId(file)).andExpect(status().isConflict());
        processor.processNext();
        fileList(manager, object, Map.of()).andExpect(status().isNotFound());
        assertThat(count("files")).isZero();
        String snapshot = jdbc.queryForObject(
                "select snapshot::text from object_deletion_audit where object_id=?", String.class, object);
        assertThat(json.readTree(snapshot).path("files").size()).isEqualTo(2);
        assertThat(json.readTree(snapshot).path("history").size()).isEqualTo(3);
        for (UUID id : List.of(fileId(file), fileId(deleted)))
            assertThatThrownBy(() -> minio.statObject(io.minio.StatObjectArgs.builder()
                            .bucket(FILE_BUCKET)
                            .object("objects/" + object + "/" + id)
                            .build()))
                    .isInstanceOf(io.minio.errors.ErrorResponseException.class);
    }

    @Test
    void twoDeletesCreateOnlyOneHistoryEntry() throws Exception {
        UUID object = add("Concurrent delete", manager, region);
        var file = uploaded(manager, object, "OTHER");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> delete = () -> {
                barrier.await();
                return deleteFile(manager, object, fileId(file))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = pool.submit(delete);
            var second = pool.submit(delete);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        }
        assertThat(count("object_history")).isEqualTo(2);
    }

    void expire() {
        jdbc.update("update file_upload_reservations set next_attempt_at=clock_timestamp()");
    }
}
