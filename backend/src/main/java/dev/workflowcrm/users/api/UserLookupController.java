package dev.workflowcrm.users.api;

import dev.workflowcrm.users.domain.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
class UserLookupController {
    private final UserLookupService users;

    UserLookupController(UserLookupService users) {
        this.users = users;
    }

    @GetMapping("/api/regions")
    ResponseEntity<List<UserRegionCatalog.Option>> regions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.regions());
    }

    @GetMapping("/api/managers")
    ResponseEntity<UserPage<UserLookupService.Manager>> managers(
            @RequestParam UUID regionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(UserPage.of(users.managers(RoleCode.MANAGER, regionId, UserStatus.ACTIVE, page, size)));
    }
}
