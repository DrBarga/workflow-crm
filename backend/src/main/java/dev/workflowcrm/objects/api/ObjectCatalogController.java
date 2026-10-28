package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectCatalogController {
    private final ObjectCatalogService catalogs;

    ObjectCatalogController(ObjectCatalogService catalogs) {
        this.catalogs = catalogs;
    }

    @GetMapping("/api/data-sources")
    ResponseEntity<List<ObjectCatalogStore.Option>> dataSources(JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(catalogs.dataSources(ObjectStatusController.actor(auth)));
    }

    @GetMapping("/api/contract-types")
    ResponseEntity<List<ObjectCatalogStore.Option>> contractTypes(JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(catalogs.contractTypes(ObjectStatusController.actor(auth)));
    }
}
