package dev.workflowcrm.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalTestUserSeedTest {
    @Test
    void emptyEmailSkipsWithoutTouchingDatabaseOrPassword() {
        var database = mock(JdbcTemplate.class);
        var passwords = mock(PasswordEncoder.class);
        new LocalTestUserSeed(database, passwords, "", "").run(new DefaultApplicationArguments());
        verifyNoInteractions(database, passwords);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "stage", "prod", "dev,prod", "dev,stage"})
    void seedIsAbsentOutsideLocalDev(String profiles) {
        context(profiles).run(context -> assertThat(context).doesNotHaveBean(LocalTestUserSeed.class));
    }

    @Test
    void seedIsPresentInDev() {
        context("dev").run(context -> assertThat(context).hasSingleBean(LocalTestUserSeed.class));
    }

    private ApplicationContextRunner context(String profiles) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profiles)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withUserConfiguration(LocalTestUserSeed.class);
    }
}
