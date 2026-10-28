package dev.workflowcrm.auth.infrastructure;

import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.csrf.CsrfFilter;

class CookieCsrfProtection implements ObjectPostProcessor<CsrfFilter> {

    @Override
    public <O extends CsrfFilter> O postProcess(O filter) {
        // Cookie-based JWTs need CSRF checks even when the resource server recognizes a bearer token.
        filter.setRequireCsrfProtectionMatcher(request -> CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                && !(request.getContextPath() + "/api/auth/login").equals(request.getRequestURI())
                && !(request.getContextPath() + "/api/auth/forgot-password").equals(request.getRequestURI())
                && !(request.getContextPath() + "/api/auth/reset-password").equals(request.getRequestURI())
                && !(request.getContextPath() + "/api/auth/telegram/webhook").equals(request.getRequestURI()));
        return filter;
    }
}
