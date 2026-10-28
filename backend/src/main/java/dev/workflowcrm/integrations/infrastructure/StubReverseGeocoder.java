package dev.workflowcrm.integrations.infrastructure;

import dev.workflowcrm.integrations.api.ReverseGeocoder;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ExternalIntegrationsMode.PROPERTY, havingValue = ExternalIntegrationsMode.MOCK)
class StubReverseGeocoder implements ReverseGeocoder {
    @Override
    public Location resolve(BigDecimal longitude, BigDecimal latitude) {
        String address = StubGeocodingLocation.ADDRESS_PREFIX + " ("
                + latitude.stripTrailingZeros().toPlainString() + ", "
                + longitude.stripTrailingZeros().toPlainString() + ")";
        return new Location(address, StubGeocodingLocation.REGION_CODE, StubGeocodingLocation.REGION_NAME);
    }
}
