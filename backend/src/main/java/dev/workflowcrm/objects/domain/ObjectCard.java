package dev.workflowcrm.objects.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface ObjectCard {
    record Pin(String name, CreatedObjectView.Coordinates entrance) implements ObjectCard {}

    record Full(
            @JsonUnwrapped CreatedObjectView object,
            ObjectCardWorkflow workflow,
            String technicalDescription,
            String commercialDescription,
            List<ObjectCardTransition> availableTransitions,
            List<ObjectCardRequest> pendingRequests,
            Map<String, Boolean> editableFields)
            implements ObjectCard {}

    record Technical(
            String name,
            CreatedObjectView.Coordinates entrance,
            TechnicalSpecifications technicalSpecifications,
            String technicalDescription,
            String contractType,
            UUID contractTypeId,
            LocalDate contractSignedDate,
            LocalDate constructionAcceptancePlannedDate,
            LocalDate constructionAcceptanceActualDate,
            LocalDate preliminaryContractSignedDate,
            LocalDate commissioningDate,
            LocalDate openingPlannedDate,
            long version,
            String googleMapsUrl,
            List<ObjectCardTransition> availableTransitions,
            List<ObjectCardRequest> pendingRequests,
            Map<String, Boolean> editableFields)
            implements ObjectCard {}
}
