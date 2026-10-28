package dev.workflowcrm.integrations.infrastructure;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioProperties.class)
class MinioConfiguration {

    @Bean
    MinioClient minioClient(MinioProperties properties) {
        var client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
        client.setTimeout(5000, 30000, 30000);
        return client;
    }
}
