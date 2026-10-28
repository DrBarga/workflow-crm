package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import dev.workflowcrm.auth.domain.ChallengePolicy;
import dev.workflowcrm.auth.domain.LoginCredentials;
import dev.workflowcrm.auth.domain.LoginFlowService;
import dev.workflowcrm.auth.domain.LoginResult;
import dev.workflowcrm.auth.domain.LoginStart;
import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import dev.workflowcrm.auth.domain.SessionMetadata;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth")
class LoginController {

    private final LoginFlowService loginFlow;
    private final AuthenticationCookieFactory cookieFactory;
    private final RefreshCookieFactory refreshCookieFactory;
    private final DeviceCookie deviceCookie;
    private final ChallengePolicy challengePolicy;

    LoginController(
            LoginFlowService loginFlow,
            AuthenticationCookieFactory cookieFactory,
            RefreshCookieFactory refreshCookieFactory,
            DeviceCookie deviceCookie,
            ChallengePolicy challengePolicy) {
        this.loginFlow = loginFlow;
        this.cookieFactory = cookieFactory;
        this.refreshCookieFactory = refreshCookieFactory;
        this.deviceCookie = deviceCookie;
        this.challengePolicy = challengePolicy;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var presented = WebUtils.getCookie(httpRequest, deviceCookie.name());
        String deviceValue = presented == null ? "" : presented.getValue();
        if (deviceCookie.deviceId(deviceValue).isBlank()) {
            deviceValue = deviceCookie.newValue();
        }
        LoginStart start = loginFlow.start(
                new LoginCredentials(request.email(), request.password()),
                new SessionMetadata(
                        deviceCookie.deviceId(deviceValue),
                        bounded(httpRequest.getHeader("User-Agent"), 512),
                        bounded(httpRequest.getRemoteAddr(), 64)));
        if (start.challenge() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.SET_COOKIE, deviceCookie.create(deviceValue))
                    .body(new ChallengeResponse(
                            start.challenge().id(),
                            start.challenge().status().name(),
                            Math.toIntExact(challengePolicy.ttl().toSeconds())));
        }
        LoginResult result = start.completed();
        LoginResponse response = new LoginResponse(result.id(), result.role(), result.regionId(), result.name());
        var responseBuilder = ResponseEntity.ok();
        if (presented == null || !presented.getValue().equals(deviceValue)) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, deviceCookie.create(deviceValue));
        }
        return responseBuilder
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.create(result.token()),
                        refreshCookieFactory.create(result.refreshToken()))
                .body(response);
    }

    private String bounded(String value, int maximumLength) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), maximumLength));
    }
}
