package dev.workflowcrm.integrations.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.BinaryFileStorage;
import dev.workflowcrm.integrations.api.BinaryStorageException;
import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

@Component
class MinioFileStorage implements BinaryFileStorage {
    private final MinioClient client;
    private final MinioClient signer;
    private final ObjectMapper json;

    MinioFileStorage(MinioClient client, MinioProperties properties, ObjectMapper json) {
        this.client = client;
        this.json = json;
        signer = MinioClient.builder()
                .endpoint(
                        properties.publicEndpoint() == null
                                        || properties.publicEndpoint().isBlank()
                                ? properties.endpoint()
                                : properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    public void upload(String bucket, String key, InputStream input, long size, String contentType) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (io.minio.errors.ErrorResponseException race) {
                    if (!java.util.Set.of("BucketAlreadyExists", "BucketAlreadyOwnedByYou")
                            .contains(race.errorResponse().code())) throw race;
                }
            }
            requirePrivateBucket(bucket);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception failure) {
            throw new BinaryStorageException(failure);
        }
    }

    public String downloadLink(String bucket, String key, String name, Duration lifetime) {
        try {
            requirePrivateBucket(bucket);
            client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(key).build());
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(Math.toIntExact(lifetime.toSeconds()))
                    .extraQueryParams(Map.of(
                            "response-content-type",
                            "application/octet-stream",
                            "response-content-disposition",
                            ContentDisposition.attachment()
                                    .filename(name, StandardCharsets.UTF_8)
                                    .build()
                                    .toString()))
                    .build());
        } catch (Exception failure) {
            throw new BinaryStorageException(failure);
        }
    }

    private void requirePrivateBucket(String bucket) throws Exception {
        String policy;
        try {
            policy = client.getBucketPolicy(
                    GetBucketPolicyArgs.builder().bucket(bucket).build());
        } catch (io.minio.errors.ErrorResponseException missing) {
            if ("NoSuchBucketPolicy".equals(missing.errorResponse().code())) return;
            throw missing;
        }
        if (policy == null || policy.isBlank()) return;
        var statements = json.readTree(policy).path("Statement");
        if (statements.isObject()) rejectAnonymousAllow(statements);
        else for (var statement : statements) rejectAnonymousAllow(statement);
    }

    private void rejectAnonymousAllow(JsonNode statement) {
        if ("Allow".equals(statement.path("Effect").asText()) && anonymous(statement.path("Principal")))
            throw new IllegalStateException("File bucket grants anonymous access");
    }

    private boolean anonymous(JsonNode principal) {
        if (principal.isTextual()) return "*".equals(principal.asText());
        for (var value : principal) if (anonymous(value)) return true;
        return false;
    }
}
