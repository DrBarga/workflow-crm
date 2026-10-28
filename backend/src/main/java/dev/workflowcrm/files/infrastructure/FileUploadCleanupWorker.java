package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.domain.FileUploadCleanup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.scheduling.annotation.EnableScheduling
@ConditionalOnProperty(name = "app.files.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class FileUploadCleanupWorker {
    private final FileUploadCleanup cleanup;

    FileUploadCleanupWorker(FileUploadCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${app.files.cleanup-interval:60000}")
    void clean() {
        for (int i = 0; i < 20 && cleanup.processNext(); i++) {}
    }
}
