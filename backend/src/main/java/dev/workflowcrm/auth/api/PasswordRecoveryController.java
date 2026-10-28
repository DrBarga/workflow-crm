package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.PasswordRecoveryService;
import dev.workflowcrm.auth.domain.PasswordResetRequestResult;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class PasswordRecoveryController {
    private final PasswordRecoveryService passwordRecovery;

    PasswordRecoveryController(PasswordRecoveryService passwordRecovery) {
        this.passwordRecovery = passwordRecovery;
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        PasswordResetRequestResult result = passwordRecovery.request(request.email());
        if (result == PasswordResetRequestResult.DELIVERY_UNAVAILABLE) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is temporarily unavailable");
        }
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecovery.reset(request.token(), request.password());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
