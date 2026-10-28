package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.UUID;

public interface IcDecisionFingerprint {
    String token(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition transition,
            IcDecisionCommand command,
            TaskTransitionPlan plan);
}
