package dev.workflowcrm.integrations.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        String publicEndpoint,
        @org.springframework.boot.context.properties.bind.DefaultValue("us-east-1") @NotBlank String region) {}
