package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.integrations.api.*;
import dev.workflowcrm.objects.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class MinioObjectExportFiles implements ObjectExportFiles {
    private final BinaryFileStorage storage;
    private final BinaryStorage deletion;

    MinioObjectExportFiles(BinaryFileStorage storage, BinaryStorage deletion) {
        this.storage = storage;
        this.deletion = deletion;
    }

    public void upload(ObjectExport.Job job, Path file) throws IOException {
        try (var content = Files.newInputStream(file)) {
            storage.upload(
                    job.bucket(),
                    job.key(),
                    content,
                    Files.size(file),
                    job.format().contentType());
        }
    }

    public String link(ObjectExport.Job job, Duration lifetime) {
        return storage.downloadLink(job.bucket(), job.key(), job.filename(), lifetime);
    }

    public void purge(ObjectExport.Job job) {
        deletion.deleteAllVersions(job.bucket(), job.key());
    }
}
