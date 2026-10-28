package dev.workflowcrm.integrations.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.BinaryStorageException;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MinioFileStorageTest {
    @Test
    void unavailableServerProducesStorageErrorForUploadAndDownload() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String endpoint = "http://127.0.0.1:" + port;
        var client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("test-access", "test-secret")
                .region("us-east-1")
                .build();
        client.setTimeout(100, 500, 500);
        var adapter = new MinioFileStorage(
                client,
                new MinioProperties(endpoint, "test-access", "test-secret", endpoint, "us-east-1"),
                new ObjectMapper());
        assertThatThrownBy(() -> adapter.upload(
                        "test-files", "objects/test/file", new ByteArrayInputStream(new byte[1]), 1, "application/pdf"))
                .isInstanceOf(BinaryStorageException.class)
                .hasRootCauseInstanceOf(java.net.ConnectException.class);
        assertThatThrownBy(() ->
                        adapter.downloadLink("test-files", "objects/test/file", "test.pdf", Duration.ofMinutes(5)))
                .isInstanceOf(BinaryStorageException.class)
                .hasRootCauseInstanceOf(java.net.ConnectException.class);
    }
}
