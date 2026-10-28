package dev.workflowcrm.integrations.infrastructure;

import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    MinioHealthIndicator(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        try {
            int bucketCount = minioClient.listBuckets().size();
            return Health.up().withDetail("buckets", bucketCount).build();
        } catch (Exception exception) {
            return Health.down().withException(exception).build();
        }
    }
}
