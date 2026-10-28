package dev.workflowcrm.objects.domain;

public class ObjectExportLimitException extends RuntimeException {
    public ObjectExportLimitException() {
        super("Export exceeds file or spreadsheet cell limits; narrow filters or use CSV");
    }
}
