package dev.workflowcrm.auth.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class CsrfController {

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }
}
