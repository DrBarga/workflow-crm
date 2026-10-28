package dev.workflowcrm.objects.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ObjectDuplicatePropertiesTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(Settings.class);

    @Test
    void invalidSearchSettingsPreventApplicationStartup() {
        for (String radius : new String[] {"0", "-1", "10001", "NaN"}) {
            context.withPropertyValues(
                            "app.objects.duplicates.radius-metres=" + radius,
                            "app.objects.duplicates.name-similarity=0.6")
                    .run(c -> assertThat(c).hasFailed());
        }
        for (String threshold : new String[] {"0", "1", "-0.5", "NaN"}) {
            context.withPropertyValues(
                            "app.objects.duplicates.radius-metres=50",
                            "app.objects.duplicates.name-similarity=" + threshold)
                    .run(c -> assertThat(c).hasFailed());
        }
    }

    @Test
    void validConfiguredRadiusAndThresholdBind() {
        context.withPropertyValues(
                        "app.objects.duplicates.radius-metres=120", "app.objects.duplicates.name-similarity=0.75")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(ObjectDuplicateProperties.class).radiusMetres())
                            .isEqualTo(120);
                    assertThat(c.getBean(ObjectDuplicateProperties.class).nameSimilarity())
                            .isEqualTo(0.75);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ObjectDuplicateProperties.class)
    static class Settings {}
}
