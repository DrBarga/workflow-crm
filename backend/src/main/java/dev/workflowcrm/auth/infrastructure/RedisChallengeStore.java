package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.ChallengePolicy;
import dev.workflowcrm.auth.domain.ChallengeStatus;
import dev.workflowcrm.auth.domain.ChallengeStore;
import dev.workflowcrm.auth.domain.LoginChallenge;
import dev.workflowcrm.auth.domain.VerifiedLogin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class RedisChallengeStore implements ChallengeStore {
    private static final String CHALLENGE = "auth:login-challenge:";
    private static final long BINDING_TTL_MILLIS = 900_000;
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'email', ARGV[2], 'deviceId', ARGV[3], "
                    + "'userAgent', ARGV[4], 'ip', ARGV[5], 'status', ARGV[6], 'codeDigest', ARGV[7]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[8]) redis.call('SETEX', KEYS[2], 10, '1') return 1",
            Long.class);
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) " + "if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end return n",
            Long.class);
    private static final DefaultRedisScript<Long> TRANSITION = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'status') ~= ARGV[1] then return 0 end "
                    + "if ARGV[3] ~= '' and redis.call('HGET', KEYS[1], 'deviceId') ~= ARGV[3] then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'status', ARGV[2]) "
                    + "if ARGV[2] == 'BINDING_REQUIRED' then redis.call('PEXPIRE', KEYS[1], ARGV[4]) end return 1",
            Long.class);
    private static final DefaultRedisScript<Long> CONFIRM_EMAIL = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'status') ~= 'PENDING_EMAIL' then return 0 end "
                    + "if redis.call('HGET', KEYS[1], 'deviceId') ~= ARGV[1] then return 0 end "
                    + "local attempts = redis.call('INCR', KEYS[2]) "
                    + "if attempts == 1 then local ttl = redis.call('PTTL', KEYS[1]); "
                    + "if ttl > 0 then redis.call('PEXPIRE', KEYS[2], ttl) end end "
                    + "if attempts > tonumber(ARGV[3]) then return 0 end "
                    + "if redis.call('HGET', KEYS[1], 'codeDigest') ~= ARGV[2] then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'status', ARGV[4]) "
                    + "redis.call('HDEL', KEYS[1], 'codeDigest') "
                    + "if ARGV[4] == 'BINDING_REQUIRED' then redis.call('PEXPIRE', KEYS[1], ARGV[5]) end "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final ChallengePolicy policy;
    private final SecureRandom random = new SecureRandom();

    RedisChallengeStore(StringRedisTemplate redis, ChallengePolicy policy) {
        this.redis = redis;
        this.policy = policy;
    }

    @Override
    public void reserve(UUID userId) {
        String key = "auth:challenge-rate:" + userId;
        Long count = redis.execute(RATE, List.of(key));
        if (count == null || count > 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login challenges");
        }
    }

    @Override
    public LoginChallenge create(VerifiedLogin login, ChallengeStatus status, String emailCode) {
        if (status != ChallengeStatus.PENDING_EMAIL && status != ChallengeStatus.PENDING_TELEGRAM) {
            throw new IllegalArgumentException("Invalid initial challenge status");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            String id = randomToken();
            LoginChallenge challenge = new LoginChallenge(
                    id,
                    login.user().id(),
                    login.email(),
                    login.metadata().deviceId(),
                    login.metadata().userAgent(),
                    login.metadata().ip(),
                    status);
            String key = CHALLENGE + id;
            Long created = redis.execute(
                    CREATE,
                    List.of(key, key + ":resend"),
                    challenge.userId().toString(),
                    challenge.email(),
                    challenge.deviceId(),
                    challenge.userAgent(),
                    challenge.ip(),
                    status.name(),
                    emailCode == null ? "" : digest(id + ":" + emailCode),
                    String.valueOf(policy.ttl().toMillis()));
            if (Long.valueOf(1).equals(created)) {
                return challenge;
            }
        }
        throw new IllegalStateException("Unable to allocate a login challenge");
    }

    @Override
    public Optional<LoginChallenge> find(String id) {
        if (!validId(id)) {
            return Optional.empty();
        }
        Map<Object, Object> fields = redis.opsForHash().entries(CHALLENGE + id);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LoginChallenge(
                id,
                UUID.fromString(fields.get("userId").toString()),
                fields.get("email").toString(),
                fields.get("deviceId").toString(),
                fields.get("userAgent").toString(),
                fields.get("ip").toString(),
                ChallengeStatus.valueOf(fields.get("status").toString())));
    }

    @Override
    public boolean confirmEmail(String id, String deviceId, String code, ChallengeStatus next) {
        if (!validId(id)
                || deviceId == null
                || code == null
                || !code.matches("[0-9]{6}")
                || (next != ChallengeStatus.APPROVED && next != ChallengeStatus.BINDING_REQUIRED)) {
            return false;
        }
        String key = CHALLENGE + id;
        Long result = redis.execute(
                CONFIRM_EMAIL,
                List.of(key, key + ":email-attempts"),
                deviceId,
                digest(id + ":" + code),
                String.valueOf(policy.maxEmailAttempts()),
                next.name(),
                String.valueOf(BINDING_TTL_MILLIS));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public boolean transition(String id, ChallengeStatus expected, ChallengeStatus next) {
        return transition(id, expected, next, "");
    }

    @Override
    public Optional<LoginChallenge> consume(String id, String deviceId) {
        Optional<LoginChallenge> challenge = find(id);
        return challenge.filter(value -> transition(id, ChallengeStatus.APPROVED, ChallengeStatus.CONSUMED, deviceId));
    }

    @Override
    public boolean mayResend(String id) {
        if (!validId(id)) {
            return false;
        }
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(CHALLENGE + id + ":resend", "1", Duration.ofSeconds(10)));
    }

    private boolean transition(String id, ChallengeStatus expected, ChallengeStatus next, String deviceId) {
        if (!validId(id)) {
            return false;
        }
        Long result = redis.execute(
                TRANSITION,
                List.of(CHALLENGE + id),
                expected.name(),
                next.name(),
                deviceId,
                String.valueOf(BINDING_TTL_MILLIS));
        return Long.valueOf(1).equals(result);
    }

    private boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{43}");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
