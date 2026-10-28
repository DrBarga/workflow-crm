package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskAssignment;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import dev.workflowcrm.tasks.domain.DeadlineRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskTemplateQuery {
    private final JdbcTemplate jdbc;
    private final JdbcTaskTemplateResolver resolver;

    JdbcTaskTemplateQuery(JdbcTemplate jdbc, JdbcTaskTemplateResolver resolver) {
        this.jdbc = jdbc;
        this.resolver = resolver;
    }

    Optional<TaskTransitionPlan> find(ObjectTaskTransition.Context context) {
        var selection = resolver.find(context);
        if (selection.isEmpty()) return Optional.empty();
        var template = selection.get();
        var items = jdbc.query(
                """
                select id, code, name, assignee_id, deadline_rule, deadline_offset_days, version,needs_review,
                    result_recipient_role_id,escalation_days,assignment_rule,role_id from task_template_items
                where template_id = ? order by sort_order, id for share
                """,
                (row, n) -> new Item(
                        row.getObject("id", UUID.class),
                        row.getString("code"),
                        row.getString("name"),
                        row.getObject("assignee_id", UUID.class),
                        new DeadlineRule(
                                DeadlineRule.Basis.valueOf(row.getString("deadline_rule")),
                                row.getInt("deadline_offset_days")),
                        row.getLong("version"),
                        row.getBoolean("needs_review"),
                        row.getObject("result_recipient_role_id", UUID.class),
                        row.getObject("escalation_days", Integer.class),
                        row.getString("assignment_rule"),
                        row.getObject("role_id", UUID.class)),
                template.id());
        var dependencies = new java.util.HashMap<UUID, java.util.List<UUID>>();
        jdbc.query(
                "select item_id,predecessor_id from task_template_item_dependencies where template_id=? order by item_id,predecessor_id for share",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> dependencies
                        .computeIfAbsent(row.getObject("item_id", UUID.class), id -> new java.util.ArrayList<>())
                        .add(row.getObject("predecessor_id", UUID.class)),
                template.id());
        List<TaskTransitionPlan.Task> tasks = items.stream()
                .map(item -> new TaskTransitionPlan.Task(
                        item.id(),
                        item.name(),
                        item.assignee(),
                        item.deadline().calculate(context.startDate()),
                        null,
                        item.needsReview(),
                        item.resultRecipientRoleId(),
                        item.escalationDays(),
                        dependencies.getOrDefault(item.id(), List.of()),
                        new TaskAssignment(item.assignmentRule(), item.roleId(), null),
                        null,
                        item.code()))
                .toList();
        String revision = template.id() + ":" + template.version() + ":"
                + items.stream()
                        .map(item -> item.id() + ":" + item.version() + ":"
                                + dependencies.getOrDefault(item.id(), List.of()))
                        .collect(java.util.stream.Collectors.joining(","));
        return Optional.of(new TaskTransitionPlan(template.id(), revision, tasks, List.of()));
    }

    private record Item(
            UUID id,
            String code,
            String name,
            UUID assignee,
            DeadlineRule deadline,
            long version,
            boolean needsReview,
            UUID resultRecipientRoleId,
            Integer escalationDays,
            String assignmentRule,
            UUID roleId) {}
}
