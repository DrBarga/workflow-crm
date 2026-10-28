package dev.workflowcrm.auth.api;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class MeController {
    private final AuthenticationUserQuery users;

    MeController(AuthenticationUserQuery users) {
        this.users = users;
    }

    @GetMapping("/me")
    ResponseEntity<LoginResponse> me(@AuthenticationPrincipal Jwt jwt) {
        var user = users.findById(UUID.fromString(jwt.getSubject()))
                .filter(candidate -> candidate.active())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled"));
        return ResponseEntity.ok(new LoginResponse(user.id(), user.role(), user.regionId(), user.fullName()));
    }
}
