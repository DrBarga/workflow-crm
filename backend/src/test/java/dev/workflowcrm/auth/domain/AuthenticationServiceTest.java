package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationUserQuery userQuery;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptTracker attemptTracker;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshSessionStore sessionStore;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService =
                new AuthenticationService(userQuery, passwordEncoder, attemptTracker, tokenIssuer, sessionStore);
    }

    @Test
    void returnsProfileAndTokenForValidCredentials() {
        AuthenticationUser user = activeUser();
        when(userQuery.findByEmail("manager@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", user.passwordHash())).thenReturn(true);
        when(sessionStore.create(user.id(), SessionMetadata.EMPTY))
                .thenReturn(new IssuedRefreshToken("refresh-token", "session-id"));
        when(tokenIssuer.issue(user, "session-id")).thenReturn("signed-token");

        LoginResult result =
                authenticationService.authenticate(new LoginCredentials(" Manager@Example.com ", "correct-password"));

        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(result.regionId()).isEqualTo(user.regionId());
        assertThat(result.name()).isEqualTo("Test Manager");
        assertThat(result.token()).isEqualTo("signed-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.toString()).doesNotContain("signed-token", "refresh-token");
        verify(attemptTracker).clear("manager@example.com");
    }

    @Test
    void returnsSameGenericErrorForUnknownEmailAndWrongPassword() {
        AuthenticationUser user = activeUser();
        when(userQuery.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("password", AuthenticationServiceTestData.DUMMY_PASSWORD_HASH))
                .thenReturn(false);
        when(userQuery.findByEmail("manager@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.passwordHash())).thenReturn(false);

        assertInvalidCredentials(new LoginCredentials("missing@example.com", "password"));
        assertInvalidCredentials(new LoginCredentials("manager@example.com", "wrong-password"));

        verify(attemptTracker).registerFailure("missing@example.com");
        verify(attemptTracker).registerFailure("manager@example.com");
        verifyNoInteractions(tokenIssuer, sessionStore);
    }

    @Test
    void rejectsDisabledUserWithForbiddenStatus() {
        AuthenticationUser user =
                new AuthenticationUser(UUID.randomUUID(), "stored-hash", "Disabled User", "MANAGER", null, false);
        when(userQuery.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", user.passwordHash())).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.authenticate(
                        new LoginCredentials("disabled@example.com", "correct-password")))
                .isInstanceOfSatisfying(DisabledUserException.class, exception -> assertThat(
                                exception.getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));
        verifyNoInteractions(tokenIssuer, sessionStore);
    }

    @Test
    void rejectsTemporarilyBlockedLoginBeforeReadingUser() {
        when(attemptTracker.isBlocked("manager@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                        authenticationService.authenticate(new LoginCredentials("manager@example.com", "password")))
                .isInstanceOfSatisfying(LoginTemporarilyBlockedException.class, exception -> assertThat(
                                exception.getStatusCode().value())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()));
        verify(userQuery, never()).findByEmail("manager@example.com");
    }

    private void assertInvalidCredentials(LoginCredentials credentials) {
        assertThatThrownBy(() -> authenticationService.authenticate(credentials))
                .isInstanceOfSatisfying(InvalidCredentialsException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
                    assertThat(exception.getReason()).isEqualTo("Invalid email or password");
                });
    }

    private AuthenticationUser activeUser() {
        return new AuthenticationUser(
                UUID.randomUUID(), "stored-hash", "Test Manager", "MANAGER", UUID.randomUUID(), true);
    }

    private static final class AuthenticationServiceTestData {

        private static final String DUMMY_PASSWORD_HASH =
                "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        private AuthenticationServiceTestData() {}
    }
}
