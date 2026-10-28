package dev.workflowcrm.files.api;

import dev.workflowcrm.files.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/objects/{id}/files")
class ObjectFileController {
    private final ObjectFileService files;

    ObjectFileController(ObjectFileService files) {
        this.files = files;
    }

    @GetMapping
    ResponseEntity<ObjectFilePage> list(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(files.list(actor(authentication), id, cursor, limit));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ObjectFile> upload(
            @PathVariable UUID id, MultipartHttpServletRequest request, JwtAuthenticationToken authentication)
            throws IOException {
        UUID actor = actor(authentication);
        files.requireUpload(actor, id);
        if (!request.getMultiFileMap().keySet().equals(Set.of("file"))
                || request.getFiles("file").size() != 1) invalid("file", "Exactly one file is required");
        if (!request.getParameterMap().keySet().equals(Set.of("categoryId"))
                || request.getParameterValues("categoryId").length != 1)
            invalid("categoryId", "Exactly one categoryId is required");
        UUID category;
        try {
            category = UUID.fromString(request.getParameter("categoryId"));
            if (!category.toString().equals(request.getParameter("categoryId"))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException malformed) {
            throw invalidException("categoryId", "Valid categoryId required");
        }
        files.requireCategory(actor, id, category);
        var source = request.getFile("file");
        ObjectFile created;
        try (var input = source.getInputStream()) {
            created = files.upload(actor, id, category, source.getOriginalFilename(), input);
        }
        return ResponseEntity.created(URI.create("/api/objects/" + id + "/files/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/{fileId}")
    ResponseEntity<ObjectFileLink> link(
            @PathVariable UUID id, @PathVariable UUID fileId, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(files.link(actor(authentication), id, fileId));
    }

    @DeleteMapping("/{fileId}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id, @PathVariable UUID fileId, JwtAuthenticationToken authentication) {
        files.delete(actor(authentication), id, fileId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID actor(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    private void invalid(String field, String message) {
        throw invalidException(field, message);
    }

    private UnprocessableInputException invalidException(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
