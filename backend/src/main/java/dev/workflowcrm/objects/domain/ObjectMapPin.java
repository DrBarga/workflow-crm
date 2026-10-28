package dev.workflowcrm.objects.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record ObjectMapPin(
        UUID id,
        String name,
        CreatedObjectView.Coordinates entrance,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status) {}
