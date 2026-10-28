package dev.workflowcrm.tasks.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import dev.workflowcrm.tasks.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.*;

record TaskTemplateDefinition(
        UUID triggerStatusId, UUID contractTypeId, String legacyContractType, List<TemplateItem> items) {
    static TaskTemplateDefinition from(
            ObjectTaskTransition.Context context,
            TaskTransitionPlan plan,
            UUID contractTypeId,
            String legacyContractType) {
        var items = new ArrayList<TemplateItem>();
        for (var task : plan.tasks())
            items.add(new TemplateItem(
                    task.templateItemId(),
                    task.name(),
                    new DeadlineRule(
                            DeadlineRule.Basis.FROM_START_CALENDAR,
                            Math.toIntExact(ChronoUnit.DAYS.between(context.startDate(), task.deadline()))),
                    new AssignmentRule(
                            AssignmentRule.Kind.valueOf(task.assignment().rule()),
                            "EXPLICIT_USER".equals(task.assignment().rule()) ? task.assigneeId() : null,
                            task.assignment().roleId()),
                    items.size(),
                    Set.copyOf(task.predecessors()),
                    task.needsReview(),
                    task.resultRecipientRoleId(),
                    task.escalationDays(),
                    task.code() == null
                            ? "ITEM_"
                                    + task.templateItemId()
                                            .toString()
                                            .replace("-", "")
                                            .toUpperCase(java.util.Locale.ROOT)
                            : task.code()));
        return new TaskTemplateDefinition(context.toStatusId(), contractTypeId, legacyContractType, List.copyOf(items));
    }

    String fingerprint(ObjectMapper json) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(json.writeValueAsString(this).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot fingerprint task template definition", e);
        }
    }
}
