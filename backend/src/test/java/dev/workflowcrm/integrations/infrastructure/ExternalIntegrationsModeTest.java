package dev.workflowcrm.integrations.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.ReverseGeocoder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class ExternalIntegrationsModeTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(NominatimReverseGeocoder.class, StubReverseGeocoder.class);

    @Test
    void mockModeReplacesNominatimWithStub() {
        context.withPropertyValues(ExternalIntegrationsMode.PROPERTY + "=" + ExternalIntegrationsMode.MOCK)
                .run(started ->
                        assertThat(started).getBean(ReverseGeocoder.class).isInstanceOf(StubReverseGeocoder.class));
    }

    @Test
    void liveModeUsesNominatim() {
        context.withPropertyValues(ExternalIntegrationsMode.PROPERTY + "=" + ExternalIntegrationsMode.LIVE)
                .run(started -> assertThat(started)
                        .getBean(ReverseGeocoder.class)
                        .isInstanceOf(NominatimReverseGeocoder.class));
    }

    @Test
    void missingModeKeepsRealServices() {
        context.run(started ->
                assertThat(started).getBean(ReverseGeocoder.class).isInstanceOf(NominatimReverseGeocoder.class));
    }

    @Test
    void stubMarksAddressAsTestAndPointsToKyiv() {
        var location = new StubReverseGeocoder().resolve(new BigDecimal("30.5234000"), new BigDecimal("50.4501"));
        assertThat(location.address()).isEqualTo("Тестова адреса (50.4501, 30.5234)");
        assertThat(location.regionCode()).isEqualTo("UA-30");
        assertThat(location.regionName()).isEqualTo("Київ");
    }
}
