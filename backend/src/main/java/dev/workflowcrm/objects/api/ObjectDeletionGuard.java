package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectDeletionAvailability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ObjectDeletionGuard implements WebMvcConfigurer, HandlerInterceptor {
    private final ObjectDeletionAvailability deletions;

    ObjectDeletionGuard(ObjectDeletionAvailability deletions) {
        this.deletions = deletions;
    }

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/objects/{id}", "/api/objects/{id}/**");
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("DELETE".equals(request.getMethod())
                && handler instanceof HandlerMethod method
                && method.getBeanType() == ObjectDeletionController.class) return true;
        var parameters = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (parameters == null) return true;
        var objectId = parameters.containsKey("objectId") ? parameters.get("objectId") : parameters.get("id");
        if (objectId == null) return true;
        UUID id;
        try {
            id = UUID.fromString(objectId.toString());
        } catch (IllegalArgumentException invalid) {
            return true;
        }
        if (deletions.pending(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object deletion is in progress");
        return true;
    }
}
