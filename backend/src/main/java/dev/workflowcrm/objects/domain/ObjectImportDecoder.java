package dev.workflowcrm.objects.domain;

public interface ObjectImportDecoder {
    ObjectImport.Command decode(ObjectImport.Input row);
}
