package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.Optional;
import java.util.UUID;

public interface TaskTransitionStore {
    Optional<TaskTransitionPlan> template(ObjectTaskTransition.Context context);

    java.util.List<TaskTransitionPlan.ExistingTask> unfinished(ObjectTaskTransition.Context context);

    void cancel(java.util.List<TaskTransitionPlan.ExistingTask> tasks, UUID actor);

    UUID generate(ObjectTaskTransition.Context context, TaskTransitionPlan plan, UUID actor);

    boolean hasTasks(UUID objectId);

    boolean hasContractTypeTaskSet(UUID objectId);

    boolean hasActiveSet(ObjectTaskTransition.Context context);
}
