package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.IOException;
import java.net.URI;
import java.security.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
class ObjectImportController {
    private final ObjectImportAccess access;
    private final ObjectImportService service;
    private final ObjectImportDocuments documents;

    ObjectImportController(ObjectImportAccess access, ObjectImportService service, ObjectImportDocuments documents) {
        this.access = access;
        this.service = service;
        this.documents = documents;
    }

    @PostMapping(value = "/api/objects/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ObjectImport.View> upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file)
            throws IOException {
        UUID actor = UUID.fromString(jwt.getSubject());
        access.require(actor);
        if (file.getSize() > 20 * 1024 * 1024)
            throw new UnprocessableInputException("file", "Maximum file size is 20 MB");
        byte[] bytes = file.getBytes();
        var rows = documents.read(file.getOriginalFilename(), bytes);
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var result = service.request(actor, hash, rows);
        return ResponseEntity.accepted()
                .location(URI.create("/api/objects/imports/" + result.id()))
                .header("Cache-Control", "no-store")
                .body(result);
    }

    @GetMapping("/api/objects/import/template")
    ResponseEntity<byte[]> template(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "xlsx") String format) {
        access.require(UUID.fromString(jwt.getSubject()));
        byte[] result = documents.template(format);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header(
                        "Content-Disposition",
                        "attachment; filename=objects-import-v" + ObjectImportTemplate.VERSION + "." + format)
                .contentType(MediaType.parseMediaType(
                        format.equals("csv")
                                ? "text/csv;charset=UTF-8"
                                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(result);
    }

    @GetMapping("/api/objects/imports/{id}")
    ResponseEntity<ObjectImport.View> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(service.status(UUID.fromString(jwt.getSubject()), id));
    }

    @GetMapping("/api/objects/imports/{id}/report")
    ResponseEntity<ObjectImport.Report> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(service.report(UUID.fromString(jwt.getSubject()), id, offset, limit));
    }
}
