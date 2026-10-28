package dev.workflowcrm.integrations.infrastructure;

import dev.workflowcrm.integrations.api.ForwardGeocoder;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = ExternalIntegrationsMode.PROPERTY, havingValue = ExternalIntegrationsMode.MOCK)
class StubForwardGeocoder implements ForwardGeocoder {
    public List<Location> search(String query) {
        return List.of();
    }
}
