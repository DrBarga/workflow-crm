package dev.workflowcrm.files.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.integrations.api.BinaryStorageException;
import dev.workflowcrm.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class FileStorageExceptionHandlerTest {
    @RestController
    static class TaskConsumer {
        @GetMapping("/test/tasks/files")
        String files() {
            throw new BinaryStorageException(new java.net.ConnectException("storage-host is unavailable"));
        }
    }

    @Test
    void storageErrorUsesTheSameSafeResponseForOtherConsumers() throws Exception {
        MockMvcBuilders.standaloneSetup(new TaskConsumer())
                .setControllerAdvice(new GlobalExceptionHandler(), new FileStorageExceptionHandler())
                .build()
                .perform(get("/test/tasks/files").header("X-Correlation-Id", "storage-test"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("File storage is unavailable"))
                .andExpect(jsonPath("$.correlationId").value("storage-test"))
                .andExpect(jsonPath("$.path").value("/test/tasks/files"));
    }
}
