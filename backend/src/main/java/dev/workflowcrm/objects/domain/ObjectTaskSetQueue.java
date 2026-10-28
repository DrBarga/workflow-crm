package dev.workflowcrm.objects.domain;

import java.util.Optional;
import java.util.UUID;

public interface ObjectTaskSetQueue {
    record Submission(ObjectTaskSetJob job, String fingerprint) {}

    record Work(ObjectTaskSetJob job, ObjectTaskSetCommand command) {}

    void lockKey(UUID actor, String key);

    Optional<Submission> submission(UUID actor, String key);

    Optional<Submission> confirmation(UUID actor, UUID object, String token);

    boolean pending(UUID object);

    ObjectTaskSetJob enqueue(UUID actor, UUID object, String token, ObjectTaskSetCommand command);

    void remember(UUID actor, String key, String fingerprint, UUID job);

    ObjectTaskSetJob get(UUID id, UUID actor, UUID object);

    Optional<Work> claim();

    Optional<Work> claim(UUID id);

    Optional<UUID> delivery();

    void sent(UUID id);

    void complete(Work work, ObjectStatusResult result);

    void failed(UUID id, String error, boolean retry);

    void approvalResolved(UUID request, String decision, ObjectStatusResult result);
}
