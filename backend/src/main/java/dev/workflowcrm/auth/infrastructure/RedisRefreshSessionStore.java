package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.DeviceSession;
import dev.workflowcrm.auth.domain.DeviceTrust;
import dev.workflowcrm.auth.domain.InvalidRefreshTokenException;
import dev.workflowcrm.auth.domain.IssuedRefreshToken;
import dev.workflowcrm.auth.domain.RefreshSession;
import dev.workflowcrm.auth.domain.RefreshSessionStore;
import dev.workflowcrm.auth.domain.SessionMetadata;
import dev.workflowcrm.auth.domain.TokenPair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisRefreshSessionStore implements RefreshSessionStore {
    private static final Logger LOG = LoggerFactory.getLogger(RedisRefreshSessionStore.class);
    private static final String SESSION = "auth:refresh-session:";
    private static final String TOKEN = "auth:refresh-token:";
    private static final String GENERATION = "auth:session-generation:";
    private static final String REPLAY = "auth:refresh-replay:";
    private static final String DEVICE_SESSIONS = "auth:device-sessions:";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern ID_FORMAT = Pattern.compile("[a-f0-9]{64}");
    private static final DefaultRedisScript<String> CREATE = script("create-refresh-session.lua");
    private static final DefaultRedisScript<String> ROTATE = script("rotate-refresh-token.lua");
    private static final DefaultRedisScript<Long> REVOKE = longScript("revoke-refresh-session.lua");

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final RefreshSessionProperties properties;
    private final RefreshReplayCodec replayCodec;
    private final Clock clock;
    private final DeviceTrust deviceTrust;
    private final SecureRandom random = new SecureRandom();

    RedisRefreshSessionStore(
            StringRedisTemplate redis,
            JwtProperties jwtProperties,
            RefreshSessionProperties properties,
            RefreshReplayCodec replayCodec,
            Clock clock,
            DeviceTrust deviceTrust) {
        this.redis = redis;
        this.jwtProperties = jwtProperties;
        this.properties = properties;
        this.replayCodec = replayCodec;
        this.clock = clock;
        this.deviceTrust = deviceTrust;
        if (properties.absoluteTtl().compareTo(jwtProperties.refreshTokenTtl()) < 0
                || properties.gracePeriod().compareTo(jwtProperties.accessTokenTtl()) >= 0) {
            throw new IllegalArgumentException(
                    "Session ceiling must cover refresh TTL and grace must be shorter than access TTL");
        }
    }

    @Override
    public IssuedRefreshToken create(UUID userId, SessionMetadata metadata) {
        if (deviceTrust.blocked(userId, metadata.deviceId())) {
            throw new IllegalStateException("Blocked device cannot create a session");
        }
        Instant now = clock.instant();
        Instant absoluteExpiry = now.plus(properties.absoluteTtl());
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = randomToken();
            String tokenId = digest(token);
            String sessionId = digest(randomToken());
            String result = redis.execute(
                    CREATE,
                    List.of(SESSION + sessionId, TOKEN + tokenId, GENERATION + userId),
                    userId.toString(),
                    sessionId,
                    tokenId,
                    millis(now),
                    millis(now.plus(jwtProperties.refreshTokenTtl())),
                    millis(absoluteExpiry),
                    UUID.randomUUID().toString(),
                    metadata.deviceId(),
                    metadata.userAgent(),
                    metadata.ip());
            if ("OK".equals(result)) {
                if (!metadata.deviceId().isBlank()) {
                    String index = deviceIndex(userId, metadata.deviceId());
                    redis.opsForSet().add(index, sessionId);
                    redis.expire(index, properties.absoluteTtl());
                }
                return new IssuedRefreshToken(token, sessionId);
            }
        }
        throw new IllegalStateException("Unable to allocate an authentication session");
    }

    @Override
    public Optional<RefreshSession> findByToken(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        Map<Object, Object> record = redis.opsForHash().entries(TOKEN + digest(token));
        return record.isEmpty()
                        || deviceTrust.blocked(
                                UUID.fromString(record.get("userId").toString()),
                                record.get("deviceId").toString())
                ? Optional.empty()
                : Optional.of(new RefreshSession(
                        record.get("sessionId").toString(),
                        UUID.fromString(record.get("userId").toString())));
    }

    @Override
    public Optional<RefreshSession> findById(String sessionId) {
        if (sessionId == null || !ID_FORMAT.matcher(sessionId).matches()) {
            return Optional.empty();
        }
        Map<Object, Object> session = redis.opsForHash().entries(SESSION + sessionId);
        if (session.isEmpty() || Long.parseLong(session.get("expiresAt").toString()) <= clock.millis()) {
            return Optional.empty();
        }
        String userId = session.get("userId").toString();
        if (deviceTrust.blocked(UUID.fromString(userId), session.get("deviceId").toString())) {
            return Optional.empty();
        }
        Object generation = redis.opsForHash().get(GENERATION + userId, "generation");
        return session.get("generation").equals(generation)
                ? Optional.of(new RefreshSession(sessionId, UUID.fromString(userId)))
                : Optional.empty();
    }

    @Override
    public List<DeviceSession> findByUser(UUID userId) {
        Object generation = redis.opsForHash().get(GENERATION + userId, "generation");
        if (generation == null) {
            return List.of();
        }

        Set<String> visited = new HashSet<>();
        List<DeviceSession> result = new ArrayList<>();
        String indexPrefix = DEVICE_SESSIONS + userId + ":";
        try (Cursor<String> indexes = redis.scan(
                ScanOptions.scanOptions().match(indexPrefix + "*").count(100).build())) {
            while (indexes.hasNext()) {
                String index = indexes.next();
                Set<String> sessionIds = redis.opsForSet().members(index);
                if (sessionIds == null) {
                    continue;
                }
                for (String sessionId : sessionIds) {
                    Optional<DeviceSession> session =
                            readDeviceSession(userId, sessionId, generation.toString(), index);
                    if (session.isPresent() && visited.add(sessionId)) {
                        result.add(session.get());
                    } else if (session.isEmpty()) {
                        redis.opsForSet().remove(index, sessionId);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public TokenPair rotate(String refreshToken, RefreshSession session, String accessToken) {
        Object absolute = redis.opsForHash().get(TOKEN + digest(refreshToken), "absoluteExpiresAt");
        if (absolute == null) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        Instant expiry = Instant.ofEpochMilli(Math.min(
                now.plus(jwtProperties.refreshTokenTtl()).toEpochMilli(), Long.parseLong(absolute.toString())));
        String replacement = randomToken();
        String oldId = digest(refreshToken);
        String newId = digest(replacement);
        TokenPair candidate = new TokenPair(accessToken, replacement, expiry);
        String result = redis.execute(
                ROTATE,
                List.of(
                        SESSION + session.id(),
                        TOKEN + oldId,
                        TOKEN + newId,
                        REPLAY + oldId,
                        GENERATION + session.userId()),
                session.id(),
                session.userId().toString(),
                millis(now),
                millis(expiry),
                Long.toString(properties.gracePeriod().toMillis()),
                newId,
                replayCodec.encrypt(refreshToken, candidate),
                UUID.randomUUID().toString(),
                millis(now.plus(properties.absoluteTtl())));
        if (result != null && result.startsWith("OK:")) {
            return replayCodec.decrypt(refreshToken, result.substring(3));
        }
        if ("REUSED".equals(result)) {
            LOG.warn("Refresh token reuse detected; all sessions revoked for user {}", session.userId());
        }
        throw new InvalidRefreshTokenException();
    }

    @Override
    public boolean isLegacyAccessRevoked(String userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return true;
        }
        Object cutoff = redis.opsForHash().get(GENERATION + userId, "revokedBefore");
        return cutoff != null && issuedAt.toEpochMilli() <= Long.parseLong(cutoff.toString());
    }

    @Override
    public void revoke(String sessionId) {
        if (sessionId != null && ID_FORMAT.matcher(sessionId).matches()) {
            revoke("", sessionId);
        }
    }

    @Override
    public boolean revoke(UUID userId, String sessionId) {
        return userId != null
                && sessionId != null
                && ID_FORMAT.matcher(sessionId).matches()
                && revoke(userId.toString(), sessionId);
    }

    @Override
    public void revokeDevice(UUID userId, String deviceId) {
        if (deviceId == null || !deviceId.matches("[a-f0-9]{64}")) {
            return;
        }
        String index = deviceIndex(userId, deviceId);
        var sessions = redis.opsForSet().members(index);
        if (sessions != null) {
            sessions.forEach(this::revoke);
        }
        redis.delete(index);
    }

    @Override
    public void revokeUser(UUID userId) {
        redis.opsForHash()
                .putAll(
                        GENERATION + userId,
                        Map.of(
                                "generation", UUID.randomUUID().toString(),
                                "revokedBefore", Long.toString(clock.millis())));
        redis.expire(GENERATION + userId, properties.absoluteTtl());
    }

    private String deviceIndex(UUID userId, String deviceId) {
        return DEVICE_SESSIONS + userId + ":" + deviceId;
    }

    private Optional<DeviceSession> readDeviceSession(
            UUID userId, String sessionId, String generation, String deviceIndex) {
        if (sessionId == null || !ID_FORMAT.matcher(sessionId).matches()) {
            return Optional.empty();
        }
        Map<Object, Object> record = redis.opsForHash().entries(SESSION + sessionId);
        try {
            String owner = record.get("userId").toString();
            String sessionGeneration = record.get("generation").toString();
            String deviceId = record.get("deviceId").toString();
            long expiresAt = Long.parseLong(record.get("expiresAt").toString());
            long createdAt = Long.parseLong(record.get("createdAt").toString());
            if (!userId.toString().equals(owner)
                    || !generation.equals(sessionGeneration)
                    || expiresAt <= clock.millis()
                    || deviceId.isBlank()
                    || !deviceIndex(userId, deviceId).equals(deviceIndex)) {
                return Optional.empty();
            }
            return Optional.of(new DeviceSession(
                    sessionId,
                    record.get("userAgent").toString(),
                    record.get("ip").toString(),
                    Instant.ofEpochMilli(createdAt)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private boolean revoke(String userId, String sessionId) {
        Long result = redis.execute(REVOKE, List.of(SESSION + sessionId), userId, sessionId, DEVICE_SESSIONS);
        return Long.valueOf(1L).equals(result);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String millis(Instant value) {
        return Long.toString(value.toEpochMilli());
    }

    private static DefaultRedisScript<String> script(String name) {
        var script = new DefaultRedisScript<String>();
        script.setLocation(new ClassPathResource("auth/" + name));
        script.setResultType(String.class);
        return script;
    }

    private static DefaultRedisScript<Long> longScript(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("auth/" + name));
        script.setResultType(Long.class);
        return script;
    }
}
