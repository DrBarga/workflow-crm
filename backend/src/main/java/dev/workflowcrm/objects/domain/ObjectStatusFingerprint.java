package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.UUID;

public interface ObjectStatusFingerprint {
    String token(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            TaskTransitionPlan plan);

    String encode(ObjectStatusCommand command);

    String encode(ObjectTaskSetCommand command);

    ObjectStatusCommand decode(String value);
}
