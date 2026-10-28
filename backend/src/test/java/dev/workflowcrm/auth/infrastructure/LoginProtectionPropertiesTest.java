package dev.workflowcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LoginProtectionPropertiesTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void acceptsSharedLockDurationOverride() {
        context.withPropertyValues("app.security.login.max-attempts=5", "app.security.login.lock-duration=2m")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application
                                    .getBean(LoginProtectionProperties.class)
                                    .lockDuration())
                            .isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Test
    void refusesDisabledOrNegativeLoginProtection() {
        context.withPropertyValues("app.security.login.max-attempts=0", "app.security.login.lock-duration=15m")
                .run(application -> assertThat(application).hasFailed());
        context.withPropertyValues("app.security.login.max-attempts=5", "app.security.login.lock-duration=0s")
                .run(application -> assertThat(application).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoginProtectionProperties.class)
    static class PropertiesConfiguration {}
}
