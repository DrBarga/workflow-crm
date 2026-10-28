package dev.workflowcrm.users.domain;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.users")
public record UserProvisioningProperties(
        Set<RoleCode> regionRequiredRoles, Duration invitationTtl, String invitationUrl, String mailFrom) {
    public UserProvisioningProperties {
        regionRequiredRoles = regionRequiredRoles == null ? Set.of() : Set.copyOf(regionRequiredRoles);
        invitationTtl = invitationTtl == null ? Duration.ofHours(72) : invitationTtl;
        invitationUrl = invitationUrl == null ? "" : invitationUrl;
        mailFrom = mailFrom == null ? "" : mailFrom;
        if (invitationTtl.isZero() || invitationTtl.isNegative()) {
            throw new IllegalArgumentException("Invitation TTL must be positive");
        }
    }
}
