package dev.workflowcrm.users.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.workflowcrm.users.domain.RoleCode;
import dev.workflowcrm.users.domain.UserStatus;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public class UpdateUserRequest {
    private RoleCode role;
    private UUID regionId;
    private boolean regionProvided;
    private java.util.List<UUID> regionIds;
    private boolean regionsProvided;
    private UserStatus status;

    @PositiveOrZero
    private Long version;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRole(RoleCode role) {
        this.role = role;
    }

    public void setRegionId(UUID regionId) {
        if (regionsProvided) throw new IllegalArgumentException("Use regionIds or regionId");
        this.regionId = regionId;
        this.regionProvided = true;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRegionIds(java.util.List<UUID> regionIds) {
        if (regionProvided) throw new IllegalArgumentException("Use regionIds or regionId");
        this.regionIds = UserRegionInput.resolve(null, regionIds);
        this.regionsProvided = true;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setVersion(Long version) {
        this.version = version;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown user update field");
    }

    public RoleCode role() {
        return role;
    }

    public UUID regionId() {
        return regionId;
    }

    public boolean regionProvided() {
        return regionProvided || regionsProvided;
    }

    public java.util.List<UUID> regionIds() {
        return regionsProvided ? regionIds : UserRegionInput.resolve(regionId, null);
    }

    public UserStatus status() {
        return status;
    }

    public Long version() {
        return version;
    }
}
