package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectDeletionProcessor;
import io.minio.*;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;

abstract class ObjectDeletionTestSupport extends ObjectListTestSupport {
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-07-23T15-54-02Z")
            .withEnv("MINIO_ROOT_USER", "deletion-test")
            .withEnv("MINIO_ROOT_PASSWORD", "deletion-test-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("app.minio.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("app.minio.access-key", () -> "deletion-test");
        r.add("app.minio.secret-key", () -> "deletion-test-secret");
    }

    @Autowired
    ObjectDeletionProcessor processor;

    @Autowired
    MinioClient minio;

    ResultActions remove(UUID actor, UUID object) throws Exception {
        return mvc.perform(
                delete("/api/objects/" + object).cookie(cookie(actor), csrf).header("X-XSRF-TOKEN", csrfToken));
    }

    JsonNode accepted(UUID object) throws Exception {
        return json.readTree(remove(admin, object)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    ResultActions operation(UUID actor, UUID operation) throws Exception {
        return mvc.perform(get("/api/object-deletions/" + operation).cookie(cookie(actor)));
    }

    String bucket() throws Exception {
        String bucket = "objects-" + UUID.randomUUID();
        minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        return bucket;
    }

    UUID file(UUID object, UUID task, String bucket, String key) throws Exception {
        put(bucket, key);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into files(id,object_id,task_id,author_id,bucket,storage_key,original_name,content_type,size_bytes) values(?,?,?,?,?,?,'document.txt','text/plain',7)",
                id,
                object,
                task,
                manager,
                bucket,
                key);
        return id;
    }

    void put(String bucket, String key) throws Exception {
        minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(
                        new ByteArrayInputStream("content".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 7, -1)
                .build());
    }

    long stored(String bucket) throws Exception {
        long count = 0;
        for (var item : minio.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .recursive(true)
                .includeVersions(true)
                .build())) {
            item.get();
            count++;
        }
        return count;
    }

    UUID task(UUID object, String state) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into tasks(id,object_id,source_status_id,name,assignee_id,deadline,status,author_id) select ?,id,status_id,'Task',?,current_date,?,? from objects where id=?",
                id,
                manager,
                state,
                manager,
                object);
        return id;
    }

    void retry(UUID operation) {
        jdbc.update("update object_deletion_operations set next_attempt_at=clock_timestamp() where id=?", operation);
    }
}
