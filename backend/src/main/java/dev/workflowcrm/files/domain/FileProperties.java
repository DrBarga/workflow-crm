package dev.workflowcrm.files.domain;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("app.files")
public record FileProperties(
        @DefaultValue("workflow-files") String bucket,
        @DefaultValue("workflow-task-files") String taskBucket,
        @DefaultValue("20971520") long maxSizeBytes,
        @DefaultValue({"jpg", "png", "heic", "pdf", "docx", "xlsx", "dwg"}) Set<String> allowedExtensions,
        @DefaultValue("5m") Duration linkTtl) {
    public FileProperties {
        if (!validBucket(bucket) || !validBucket(taskBucket)) throw new IllegalArgumentException("Invalid file bucket");
        if (maxSizeBytes < 1 || maxSizeBytes > 20971520)
            throw new IllegalArgumentException("File limit must be 1–20971520 bytes");
        if (allowedExtensions == null
                || allowedExtensions.isEmpty()
                || !Set.of("jpg", "png", "heic", "pdf", "docx", "xlsx", "dwg").containsAll(allowedExtensions))
            throw new IllegalArgumentException("Unsupported file extensions");
        allowedExtensions = Set.copyOf(allowedExtensions);
        if (linkTtl == null || linkTtl.toSeconds() < 1 || linkTtl.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("File link lifetime must be 1 second–15 minutes");
    }

    public String bucket(dev.workflowcrm.files.api.FileOwner.Type type) {
        return type == dev.workflowcrm.files.api.FileOwner.Type.OBJECT ? bucket : taskBucket;
    }

    private static boolean validBucket(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
class FileConfiguration {}
