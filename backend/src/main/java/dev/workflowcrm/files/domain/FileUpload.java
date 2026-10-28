package dev.workflowcrm.files.domain;

public record FileUpload(String name, String contentType, byte[] content) {}
