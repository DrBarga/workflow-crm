package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.CurrentProfile;
import dev.workflowcrm.auth.domain.ProfileQuery;
import dev.workflowcrm.auth.domain.TelegramLinkIssuer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
class ProfileController {
    private final ProfileQuery profiles;
    private final TelegramLinkIssuer links;

    ProfileController(ProfileQuery profiles, TelegramLinkIssuer links) {
        this.profiles = profiles;
        this.links = links;
    }

    @GetMapping
    ResponseEntity<CurrentProfile> profile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(profiles.current(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/telegram/link")
    ResponseEntity<LinkResponse> link(@AuthenticationPrincipal Jwt jwt) {
        Instant issuedAt = Instant.now();
        String link = links.profile(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new LinkResponse(
                        link,
                        issuedAt.plus(TelegramLinkIssuer.PROFILE_TTL),
                        TelegramLinkIssuer.PROFILE_TTL.toSeconds()));
    }

    @DeleteMapping("/telegram")
    ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt) {
        links.unbind(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    record LinkResponse(String link, Instant expiresAt, long expiresInSeconds) {}
}
