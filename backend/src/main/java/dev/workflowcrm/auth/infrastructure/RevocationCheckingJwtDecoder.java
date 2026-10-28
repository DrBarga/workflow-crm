package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.RefreshSessionStore;
import dev.workflowcrm.auth.domain.TokenRevocationStore;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class RevocationCheckingJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final TokenRevocationStore revocationStore;
    private final RefreshSessionStore sessionStore;

    RevocationCheckingJwtDecoder(
            JwtDecoder delegate, TokenRevocationStore revocationStore, RefreshSessionStore sessionStore) {
        this.delegate = delegate;
        this.revocationStore = revocationStore;
        this.sessionStore = sessionStore;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);
        if (jwt.getId() == null || jwt.getId().isBlank() || jwt.getExpiresAt() == null) {
            throw new BadJwtException("Token must contain an identifier and expiration");
        }
        try {
            if (revocationStore.isRevoked(jwt.getId())) {
                throw new BadJwtException("Token has been revoked");
            }
            String sessionId = jwt.getClaimAsString("sid");
            if (sessionId == null && sessionStore.isLegacyAccessRevoked(jwt.getSubject(), jwt.getIssuedAt())) {
                throw new BadJwtException("Authentication sessions have been revoked");
            }
            // Access tokens issued before refresh sessions were introduced remain valid until expiration.
            if (sessionId != null
                    && sessionStore
                            .findById(sessionId)
                            .filter(session -> session.userId().toString().equals(jwt.getSubject()))
                            .isEmpty()) {
                throw new BadJwtException("Authentication session has expired or been revoked");
            }
        } catch (DataAccessException exception) {
            throw new JwtException("Token revocation check is unavailable", exception);
        }
        return jwt;
    }
}
