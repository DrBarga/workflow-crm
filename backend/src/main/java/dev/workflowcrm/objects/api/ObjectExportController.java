package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectExportController {
    private final ObjectExportService exports;
    private final ObjectListParser lists;

    ObjectExportController(ObjectExportService exports, ObjectListParser lists) {
        this.exports = exports;
        this.lists = lists;
    }

    @PostMapping("/api/objects/export")
    ResponseEntity<ObjectExport.View> request(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        if (!body.isObject()) throw invalid("request");
        var format = body.path("format");
        if (!format.isTextual()) throw invalid("format");
        ObjectExport.Format type;
        try {
            type = ObjectExport.Format.valueOf(format.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("format");
        }
        var parameters = new LinkedMultiValueMap<String, String>();
        body.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            if (field.equals("format")) return;
            if (Set.of("cursor", "limit").contains(field) || !entry.getValue().isTextual()) throw invalid(field);
            parameters.add(field, entry.getValue().asText());
        });
        var result = exports.request(
                UUID.fromString(jwt.getSubject()), type, lists.parse(parameters).filter());
        return ResponseEntity.accepted()
                .location(URI.create("/api/objects/exports/" + result.id()))
                .header("Cache-Control", "no-store")
                .body(result);
    }

    @GetMapping("/api/objects/exports/{id}")
    ResponseEntity<ObjectExport.View> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(exports.status(UUID.fromString(jwt.getSubject()), id));
    }

    @GetMapping("/api/objects/exports/{id}/download")
    ResponseEntity<ObjectExport.Download> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(exports.download(UUID.fromString(jwt.getSubject()), id));
    }

    private static UnprocessableInputException invalid(String field) {
        return new UnprocessableInputException(field, "Invalid export parameter");
    }
}
