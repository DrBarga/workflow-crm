package dev.workflowcrm.users.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RegionBoundaryQuery {
    Optional<UUID> locate(BigDecimal longitude, BigDecimal latitude);
}
