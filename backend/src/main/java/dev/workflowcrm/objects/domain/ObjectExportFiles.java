package dev.workflowcrm.objects.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public interface ObjectExportFiles {
    void upload(ObjectExport.Job job, Path file) throws IOException;

    String link(ObjectExport.Job job, Duration lifetime);

    void purge(ObjectExport.Job job);
}
