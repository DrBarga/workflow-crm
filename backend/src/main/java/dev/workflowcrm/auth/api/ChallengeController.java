package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import dev.workflowcrm.auth.domain.LoginFlowService;
import dev.workflowcrm.auth.domain.LoginResult;
import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/auth/challenges")
class ChallengeController {
    private final LoginFlowService loginFlow;
    private final DeviceCookie deviceCookie;
    private final AuthenticationCookieFactory accessCookies;
    private final RefreshCookieFactory refreshCookies;

    ChallengeController(
            LoginFlowService loginFlow,
            DeviceCookie deviceCookie,
            AuthenticationCookieFactory accessCookies,
            RefreshCookieFactory refreshCookies) {
        this.loginFlow = loginFlow;
        this.deviceCookie = deviceCookie;
        this.accessCookies = accessCookies;
        this.refreshCookies = refreshCookies;
    }

    @GetMapping("/{id}")
    ResponseEntity<?> status(@PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok()
                .body(new StatusResponse(loginFlow.status(id, deviceId(request)).name()));
    }

    @PostMapping("/{id}/email")
    ResponseEntity<?> confirmEmail(
            @PathVariable String id, @Valid @RequestBody EmailCodeRequest body, HttpServletRequest request) {
        String link = loginFlow.confirmEmail(id, deviceId(request), body.code());
        return ResponseEntity.ok()
                .body(new EmailConfirmationResponse(link.isBlank() ? "APPROVED" : "BINDING_REQUIRED", link));
    }

    @PostMapping("/{id}/binding-link")
    ResponseEntity<?> bindingLink(@PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok().body(new LinkResponse(loginFlow.bindingLink(id, deviceId(request))));
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<?> complete(@PathVariable String id, HttpServletRequest request) {
        LoginResult result = loginFlow.complete(id, deviceId(request));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        accessCookies.create(result.token()),
                        refreshCookies.create(result.refreshToken()))
                .body(new LoginResponse(result.id(), result.role(), result.regionId(), result.name()));
    }

    @PostMapping("/{id}/resend")
    ResponseEntity<Void> resend(@PathVariable String id, HttpServletRequest request) {
        loginFlow.resendTelegram(id, deviceId(request));
        return ResponseEntity.noContent().build();
    }

    private String deviceId(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, deviceCookie.name());
        return deviceCookie.deviceId(cookie == null ? null : cookie.getValue());
    }

    record EmailCodeRequest(@Pattern(regexp = "[0-9]{6}") String code) {}

    record StatusResponse(String status) {}

    record EmailConfirmationResponse(String status, String bindingLink) {}

    record LinkResponse(String link) {}
}
