package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "object_statuses")
public class ObjectStatus extends BaseEntity {
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_final", nullable = false)
    private boolean finalStatus;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ObjectStatus() {}

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isFinal() {
        return finalStatus;
    }

    public boolean isActive() {
        return active;
    }
}
