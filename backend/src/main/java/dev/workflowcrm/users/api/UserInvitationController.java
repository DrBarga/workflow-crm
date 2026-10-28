package dev.workflowcrm.users.api;

import dev.workflowcrm.users.domain.UserInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/user-invitations")
class UserInvitationController {
    private final UserInvitationService invitations;
    private final UserOnboardingSecurity onboarding;

    UserInvitationController(UserInvitationService invitations, UserOnboardingSecurity onboarding) {
        this.invitations = invitations;
        this.onboarding = onboarding;
    }

    @PostMapping("/open")
    ResponseEntity<OpenInvitationResponse> open(
            @Valid @RequestBody OpenInvitationRequest request, HttpServletRequest httpRequest) {
        var presented = WebUtils.getCookie(httpRequest, onboarding.deviceCookieName());
        invitations.open(request.token());
        String cookie = onboarding.prepareInvitationDevice(presented == null ? "" : presented.getValue());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(new OpenInvitationResponse("PENDING_PASSWORD_SETUP"));
    }

    @PostMapping("/accept")
    ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest httpRequest) {
        var presented = WebUtils.getCookie(httpRequest, onboarding.deviceCookieName());
        String cookie =
                invitations.accept(request.token(), request.password(), presented == null ? "" : presented.getValue());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie).build();
    }

    record OpenInvitationResponse(String deviceStatus) {}
}
