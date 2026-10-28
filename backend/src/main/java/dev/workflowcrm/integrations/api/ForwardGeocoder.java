package dev.workflowcrm.integrations.api;

import java.math.BigDecimal;
import java.util.List;

public interface ForwardGeocoder {
    record Location(String label, BigDecimal latitude, BigDecimal longitude) {}

    List<Location> search(String query);
}
