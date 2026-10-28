package dev.workflowcrm.objects.domain;

import java.util.UUID;

public interface ObjectEditFingerprint {
    String token(UUID actor, ObjectPatchCommand command, String taskToken);
}
