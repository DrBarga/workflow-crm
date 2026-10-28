package dev.workflowcrm.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionStore {

    IssuedRefreshToken create(UUID userId, SessionMetadata metadata);

    TokenPair rotate(String refreshToken, RefreshSession session, String accessToken);

    boolean isLegacyAccessRevoked(String userId, Instant issuedAt);

    Optional<RefreshSession> findByToken(String token);

    Optional<RefreshSession> findById(String sessionId);

    List<DeviceSession> findByUser(UUID userId);

    void revoke(String sessionId);

    boolean revoke(UUID userId, String sessionId);

    void revokeDevice(UUID userId, String deviceId);

    void revokeUser(UUID userId);
}
