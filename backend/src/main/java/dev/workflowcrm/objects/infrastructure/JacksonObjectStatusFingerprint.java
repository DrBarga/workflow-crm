package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JacksonObjectStatusFingerprint implements ObjectStatusFingerprint {
    private final ObjectMapper json;

    JacksonObjectStatusFingerprint(ObjectMapper json) {
        this.json = json;
    }

    public String token(
            UUID actor,
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            TaskTransitionPlan plan) {
        record Preview(
                UUID actor,
                UUID object,
                long version,
                UUID transition,
                long ruleVersion,
                Object policy,
                Object command,
                TaskTransitionPlan tasks) {}
        var normalized = new ObjectStatusCommand(
                command.status(),
                command.version(),
                command.refusalReasonId(),
                command.contractSignedDate(),
                command.contractTypeId(),
                command.openingDate(),
                command.openingPlannedDate(),
                command.plannedDateProvided(),
                null,
                command.signing(),
                command.assignees());
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(json.writeValueAsBytes(new Preview(
                                    actor,
                                    object.getId(),
                                    object.getVersion(),
                                    rule.getId(),
                                    rule.getVersion(),
                                    policy(rule),
                                    normalized,
                                    plan))));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint status preview", exception);
        }
    }

    private Object policy(ObjectStatusTransition rule) {
        record Policy(
                UUID target,
                Object action,
                boolean confirmation,
                UUID confirmingRole,
                Object condition,
                boolean unavailableAfterTasks,
                Object effects,
                Object roles,
                Object validationRoles,
                Object fields,
                Object statuses,
                Object requiredTasks) {}
        return new Policy(
                rule.getToStatus().getId(),
                rule.getUnfinishedTaskAction(),
                rule.isConfirmationRequired(),
                rule.getConfirmingRoleId(),
                rule.getCondition(),
                rule.isUnavailableAfterTaskCreation(),
                rule.getEffects(),
                rule.getRoleIds().stream().sorted().toList(),
                rule.getValidationRoleIds().stream().sorted().toList(),
                rule.getRequiredFields().stream().sorted().toList(),
                rule.getConditionStatusIds().stream().sorted().toList(),
                rule.getRequiredTaskCodes().stream().sorted().toList());
    }

    public String encode(ObjectStatusCommand command) {
        try {
            return json.writeValueAsString(command);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store status request", exception);
        }
    }

    public String encode(dev.workflowcrm.objects.domain.ObjectTaskSetCommand command) {
        try {
            return json.writeValueAsString(command);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store task-set command", exception);
        }
    }

    public ObjectStatusCommand decode(String value) {
        try {
            return json.readValue(value, ObjectStatusCommand.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read status request", exception);
        }
    }
}
