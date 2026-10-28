package dev.workflowcrm.integrations.api;

import java.math.BigDecimal;

public interface ReverseGeocoder {
    record Location(String address, String regionCode, String regionName) {}

    Location resolve(BigDecimal longitude, BigDecimal latitude);
}
