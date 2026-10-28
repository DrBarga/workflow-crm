package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.integrations.api.ForwardGeocoder;
import dev.workflowcrm.objects.domain.GeoLookupService;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/geo")
class GeoLookupController {
    private final GeoLookupService geo;

    GeoLookupController(GeoLookupService geo) {
        this.geo = geo;
    }

    @GetMapping("/search")
    ResponseEntity<List<ForwardGeocoder.Location>> search(
            @RequestParam String q, @RequestParam(defaultValue = "6") int limit, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(geo.search(ObjectStatusController.actor(auth), q, limit));
    }

    @PostMapping("/reverse")
    ResponseEntity<GeoLookupService.Address> reverse(@RequestBody JsonNode body, JwtAuthenticationToken auth) {
        if (body == null || !body.isObject()) throw invalid("body", "Provide a JSON object");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("longitude", "latitude").contains(field)) throw invalid(field, "Unknown field");
        });
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(geo.reverse(
                        ObjectStatusController.actor(auth), number(body, "longitude"), number(body, "latitude")));
    }

    private static BigDecimal number(JsonNode body, String field) {
        if (!body.path(field).isNumber()) throw invalid(field, "Provide a numeric coordinate");
        return body.path(field).decimalValue();
    }

    private static UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
