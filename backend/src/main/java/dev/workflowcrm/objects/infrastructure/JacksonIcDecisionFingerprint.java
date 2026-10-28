package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JacksonIcDecisionFingerprint implements IcDecisionFingerprint {
    private final ObjectMapper json;

    JacksonIcDecisionFingerprint(ObjectMapper json) {
        this.json = json;
    }

    public String token(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition transition,
            IcDecisionCommand command,
            TaskTransitionPlan plan) {
        record Preview(
                UUID actor,
                UUID id,
                long version,
                UUID transition,
                long transitionVersion,
                String action,
                InvestmentCommitteeDecision decision,
                java.time.LocalDate date,
                String conditions,
                String refusalComment,
                TaskTransitionPlan tasks) {}
        try {
            var preview = new Preview(
                    actor,
                    object.getId(),
                    object.getVersion(),
                    transition.getId(),
                    transition.getVersion(),
                    transition.getUnfinishedTaskAction().name(),
                    command.decision(),
                    command.date(),
                    command.conditions(),
                    command.refusalComment(),
                    plan);
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(preview)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot prepare IC preview", exception);
        }
    }
}
