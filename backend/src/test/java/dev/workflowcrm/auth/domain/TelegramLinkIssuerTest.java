package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.TelegramBinding;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TelegramLinkIssuerTest {
    final UUID id = UUID.randomUUID();
    final AuthenticationUserQuery users = mock(AuthenticationUserQuery.class);
    final TelegramBinding binding = mock(TelegramBinding.class);
    final BindingTokens tokens = mock(BindingTokens.class);
    final TelegramSettings settings = mock(TelegramSettings.class);
    final RefreshSessionStore sessions = mock(RefreshSessionStore.class);
    final TelegramLinkIssuer issuer =
            new TelegramLinkIssuer(users, binding, tokens, mock(ChallengeStore.class), sessions, settings);

    @BeforeEach
    void setup() {
        when(users.findById(id))
                .thenReturn(Optional.of(new AuthenticationUser(id, "hidden", "Name", "MANAGER", null, true)));
        when(settings.configured()).thenReturn(true);
        when(settings.botUsername()).thenReturn("workflow_test_bot");
    }

    @Test
    void profileUsesTenMinuteReplacementToken() {
        when(tokens.issue(id, "", TelegramLinkIssuer.PROFILE_TTL, true)).thenReturn("token");
        assertThat(issuer.profile(id)).isEqualTo("https://t.me/workflow_test_bot?start=token");
        verify(tokens).issue(id, "", java.time.Duration.ofMinutes(10), true);
    }

    @Test
    void missingConfigurationDoesNotIssueToken() {
        when(settings.configured()).thenReturn(false);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(tokens);
    }

    @Test
    void invalidBotNameDoesNotIssueToken() {
        when(settings.botUsername()).thenReturn("../bot");
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(tokens);
    }

    @Test
    void missingUserCannotIssueLink() {
        when(users.findById(id)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND);
        verifyNoInteractions(tokens);
    }

    @Test
    void disabledUserCannotIssueLink() {
        when(users.findById(id))
                .thenReturn(Optional.of(new AuthenticationUser(id, "hidden", "Name", "MANAGER", null, false)));
        assertStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(tokens);
    }

    @Test
    void unlinkDoesNotDependOnConfiguredBot() {
        when(settings.configured()).thenReturn(false);
        issuer.unbind(id);
        verify(sessions).revokeUser(id);
        verify(tokens).invalidate(id);
        verify(binding).unbind(id);
    }

    void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> issuer.profile(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode())
                        .isEqualTo(expected));
    }
}
