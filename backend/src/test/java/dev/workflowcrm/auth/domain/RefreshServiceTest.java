package dev.workflowcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private RefreshSessionStore sessionStore;

    @Mock
    private AuthenticationUserQuery userQuery;

    @Mock
    private TokenIssuer tokenIssuer;

    private RefreshService service;

    @BeforeEach
    void setUp() {
        service = new RefreshService(sessionStore, userQuery, tokenIssuer);
    }

    @Test
    void rotatesWithCurrentUserDataAndReturnsTheStoredPair() {
        UUID userId = UUID.randomUUID();
        var user = new AuthenticationUser(userId, "hash", "Manager", "MANAGER", null, true);
        when(sessionStore.findByToken("refresh")).thenReturn(Optional.of(new RefreshSession("session", userId)));
        when(userQuery.findById(userId)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user, "session")).thenReturn("access");
        var pair = new TokenPair("access", "replacement", Instant.now().plusSeconds(60));
        when(sessionStore.rotate("refresh", new RefreshSession("session", userId), "access"))
                .thenReturn(pair);

        assertThat(service.refresh("refresh")).isEqualTo(pair);
        verify(sessionStore).findByToken("refresh");
    }

    @Test
    void rejectsUnknownTokenBeforeReadingUser() {
        assertThatThrownBy(() -> service.refresh("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(userQuery, tokenIssuer);
    }

    @Test
    void revokesSessionOfDeletedUser() {
        UUID userId = UUID.randomUUID();
        when(sessionStore.findByToken("refresh")).thenReturn(Optional.of(new RefreshSession("session", userId)));
        assertThatThrownBy(() -> service.refresh("refresh")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(sessionStore).revoke("session");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void revokesSessionOfDisabledUser() {
        UUID userId = UUID.randomUUID();
        when(sessionStore.findByToken("refresh")).thenReturn(Optional.of(new RefreshSession("session", userId)));
        when(userQuery.findById(userId))
                .thenReturn(Optional.of(new AuthenticationUser(userId, "hash", "Manager", "MANAGER", null, false)));
        assertThatThrownBy(() -> service.refresh("refresh")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(sessionStore).revoke("session");
        verifyNoInteractions(tokenIssuer);
    }
}
