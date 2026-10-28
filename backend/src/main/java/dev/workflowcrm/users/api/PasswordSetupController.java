package dev.workflowcrm.users.api;

import dev.workflowcrm.users.domain.UserInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth")
class PasswordSetupController {
    private final UserInvitationService invitations;
    private final UserOnboardingSecurity onboarding;

    PasswordSetupController(UserInvitationService invitations, UserOnboardingSecurity onboarding) {
        this.invitations = invitations;
        this.onboarding = onboarding;
    }

    @GetMapping("/invite/{token}")
    ResponseEntity<UserInvitationController.OpenInvitationResponse> inspect(
            @PathVariable String token, HttpServletRequest request) {
        invitations.open(token);
        var presented = WebUtils.getCookie(request, onboarding.deviceCookieName());
        String cookie = onboarding.prepareInvitationDevice(presented == null ? "" : presented.getValue());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(new UserInvitationController.OpenInvitationResponse("PENDING_PASSWORD_SETUP"));
    }

    @PostMapping("/set-password")
    ResponseEntity<Void> setPassword(@Valid @RequestBody AcceptInvitationRequest body, HttpServletRequest request) {
        var presented = WebUtils.getCookie(request, onboarding.deviceCookieName());
        String cookie =
                invitations.accept(body.token(), body.password(), presented == null ? "" : presented.getValue());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie).build();
    }
}
