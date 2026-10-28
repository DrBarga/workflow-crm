package dev.workflowcrm.files.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_categories")
public class FileCategory extends BaseEntity {
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "is_restricted", nullable = false)
    private boolean restricted;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected FileCategory() {}

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public boolean isActive() {
        return active;
    }
}
