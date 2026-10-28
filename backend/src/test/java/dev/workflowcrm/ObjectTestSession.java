package dev.workflowcrm;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

final class ObjectTestSession {
    private ObjectTestSession() {}

    static Cookie cookie(JwtEncoder tokens, UUID actor) {
        var claims = JwtClaimsSet.builder()
                .subject(actor.toString())
                .issuer("object-test")
                .id(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("role", "MANAGER")
                .build();
        return new Cookie(
                "workflow_access_token",
                tokens.encode(JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue());
    }
}
