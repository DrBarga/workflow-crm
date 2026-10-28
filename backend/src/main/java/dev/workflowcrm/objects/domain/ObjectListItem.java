package dev.workflowcrm.objects.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public sealed interface ObjectListItem {
    record Pin(String name, CreatedObjectView.Coordinates entrance) implements ObjectListItem {}

    record Technical(
            String name,
            CreatedObjectView.Coordinates entrance,
            TechnicalSpecifications technicalSpecifications,
            String technicalDescription)
            implements ObjectListItem {}

    record Full(
            @JsonUnwrapped CreatedObjectView object,
            ObjectListWorkflow workflow,
            String technicalDescription,
            String commercialDescription)
            implements ObjectListItem {}
}
