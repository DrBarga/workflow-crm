package dev.workflowcrm.objects.domain;

import java.io.IOException;
import java.nio.file.Path;

public interface ObjectExportRenderer {
    long render(ObjectExport.Job job, Path file, long maxBytes) throws IOException;
}
