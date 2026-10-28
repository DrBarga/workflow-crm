package dev.workflowcrm.files.api;

import dev.workflowcrm.files.domain.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class FileCategoryController {
    private final FileCategoryService categories;

    FileCategoryController(FileCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/api/file-categories")
    ResponseEntity<List<FileCategoryCatalog.Option>> list(JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(categories.list(UUID.fromString(auth.getToken().getSubject())));
    }
}
