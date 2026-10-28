package dev.workflowcrm.users.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 64)
    private RoleCode code;

    @Column(nullable = false, length = 160)
    private String name;

    protected Role() {}

    public Role(RoleCode code, String name) {
        this.code = code;
        this.name = name;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
