package dev.workflowcrm.integrations.api;

public class GeocodingUnavailableException extends org.springframework.web.server.ResponseStatusException {
    public GeocodingUnavailableException() {
        super(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Address lookup is temporarily unavailable; provide address and region manually");
    }
}
