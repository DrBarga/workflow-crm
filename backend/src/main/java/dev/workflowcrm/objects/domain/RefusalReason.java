package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "refusal_reasons")
public class RefusalReason extends BaseEntity {
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_system_only", nullable = false)
    private boolean systemOnly;

    protected RefusalReason() {}

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSystemOnly() {
        return systemOnly;
    }
}
