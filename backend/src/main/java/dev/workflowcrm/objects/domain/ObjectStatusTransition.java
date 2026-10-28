package dev.workflowcrm.objects.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "object_status_transitions")
public class ObjectStatusTransition extends ObjectTransitionPolicy {
    @jakarta.persistence.Embedded
    private TransitionEffects effects;

    @ElementCollection
    @CollectionTable(name = "object_transition_condition_statuses", joinColumns = @JoinColumn(name = "transition_id"))
    @Column(name = "status_id", nullable = false)
    private Set<UUID> conditionStatusIds = new HashSet<>();

    public TransitionEffects getEffects() {
        return effects;
    }

    public Set<UUID> getConditionStatusIds() {
        return Set.copyOf(conditionStatusIds);
    }

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_status_id", nullable = false)
    private ObjectStatus fromStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_status_id", nullable = false)
    private ObjectStatus toStatus;

    @ElementCollection
    @CollectionTable(name = "object_transition_roles", joinColumns = @JoinColumn(name = "transition_id"))
    @Column(name = "role_id", nullable = false)
    private Set<UUID> roleIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "object_transition_validation_roles", joinColumns = @JoinColumn(name = "transition_id"))
    @Column(name = "role_id", nullable = false)
    private Set<UUID> validationRoleIds = new HashSet<>();

    public Set<UUID> getValidationRoleIds() {
        return Set.copyOf(validationRoleIds);
    }

    @ElementCollection
    @CollectionTable(name = "object_transition_required_fields", joinColumns = @JoinColumn(name = "transition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "field_code", nullable = false, length = 64)
    private Set<TransitionRequiredField> requiredFields = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "object_transition_required_tasks", joinColumns = @JoinColumn(name = "transition_id"))
    @Column(name = "item_code", nullable = false, length = 100)
    private Set<String> requiredTaskCodes = new HashSet<>();

    public Set<String> getRequiredTaskCodes() {
        return Set.copyOf(requiredTaskCodes);
    }

    protected ObjectStatusTransition() {}

    public String getCode() {
        return code;
    }

    public ObjectStatus getFromStatus() {
        return fromStatus;
    }

    public ObjectStatus getToStatus() {
        return toStatus;
    }

    public Set<UUID> getRoleIds() {
        return Set.copyOf(roleIds);
    }

    public Set<TransitionRequiredField> getRequiredFields() {
        return Set.copyOf(requiredFields);
    }
}
