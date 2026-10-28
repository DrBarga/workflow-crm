package dev.workflowcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.workflowcrm.auth.domain.RefreshSession;
import dev.workflowcrm.auth.domain.RefreshSessionStore;
import dev.workflowcrm.auth.domain.TokenRevocationStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class RevocationCheckingJwtDecoderTest {

    @Mock
    private JwtDecoder delegate;

    @Mock
    private TokenRevocationStore revocationStore;

    @Mock
    private RefreshSessionStore sessionStore;

    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new RevocationCheckingJwtDecoder(delegate, revocationStore, sessionStore);
    }

    @Test
    void acceptsValidTokenThatHasNotBeenRevoked() {
        Jwt jwt = token().jti("session-id")
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(delegate.decode("signed-token")).thenReturn(jwt);

        assertThat(decoder.decode("signed-token")).isSameAs(jwt);
    }

    @Test
    void rejectsRevokedToken() {
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("session-id")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        when(revocationStore.isRevoked("session-id")).thenReturn(true);

        assertThatThrownBy(() -> decoder.decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void doesNotQueryRedisForInvalidToken() {
        when(delegate.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature or expiration"));

        assertThatThrownBy(() -> decoder.decode("invalid-token")).isInstanceOf(BadJwtException.class);
        verifyNoInteractions(revocationStore);
    }

    @Test
    void rejectsMissingIdentifierOrExpiration() {
        when(delegate.decode("without-id"))
                .thenReturn(token().expiresAt(Instant.now().plusSeconds(60)).build());
        when(delegate.decode("without-expiration"))
                .thenReturn(token().jti("session-id").build());
        when(delegate.decode("blank-id"))
                .thenReturn(token().jti(" ")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());

        assertThatThrownBy(() -> decoder.decode("without-id")).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode("without-expiration")).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode("blank-id")).isInstanceOf(BadJwtException.class);
        verifyNoInteractions(revocationStore);
    }

    @Test
    void deniesAuthenticationWhenRedisIsUnavailable() {
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("session-id")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        when(revocationStore.isRevoked("session-id"))
                .thenThrow(new RedisConnectionFailureException("Connection unavailable"));

        assertThatThrownBy(() -> decoder.decode("signed-token"))
                .isInstanceOf(JwtException.class)
                .hasMessage("Token revocation check is unavailable");
    }

    @Test
    void rejectsAccessWhenItsSessionHasBeenRemoved() {
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("id")
                        .claim("sid", "missing-session")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        assertThatThrownBy(() -> decoder.decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("id")
                        .claim("sid", "other-session")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        when(sessionStore.findById("other-session"))
                .thenReturn(Optional.of(new RefreshSession("other-session", UUID.randomUUID())));
        assertThatThrownBy(() -> decoder.decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void failsClosedWhenSessionLookupIsUnavailable() {
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("id")
                        .claim("sid", "session")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        when(sessionStore.findById("session")).thenThrow(new RedisConnectionFailureException("Connection unavailable"));
        assertThatThrownBy(() -> decoder.decode("signed-token")).isInstanceOf(JwtException.class);
    }

    @Test
    void globalRevocationAlsoRejectsLegacyTokensWithoutSessionClaims() {
        Instant issuedAt = Instant.now().minusSeconds(60);
        when(delegate.decode("signed-token"))
                .thenReturn(token().jti("legacy-id")
                        .issuedAt(issuedAt)
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
        when(sessionStore.isLegacyAccessRevoked("user-id", issuedAt)).thenReturn(true);
        assertThatThrownBy(() -> decoder.decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    private Jwt.Builder token() {
        return Jwt.withTokenValue("signed-token").header("alg", "HS256").subject("user-id");
    }
}
