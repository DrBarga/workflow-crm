package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.BindingRequest;
import dev.workflowcrm.auth.domain.BindingTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisBindingTokens implements BindingTokens {
    private static final String PREFIX = "auth:telegram-binding:";
    private static final String USER_PREFIX = "auth:telegram-binding-user:";
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>(
            "local old=redis.call('GET',KEYS[1]) "
                    + "local value=ARGV[3] if old then "
                    + "redis.call('DEL',ARGV[1]..old) end "
                    + "redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[4]) "
                    + "redis.call('SET',ARGV[1]..ARGV[2],value,'EX',ARGV[4]) return 1",
            Long.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local value=redis.call('GET',KEYS[1]) if not value then return nil end "
                    + "local owner=string.match(value,'^([^|]+)|') if not owner then return nil end "
                    + "local index=ARGV[1]..owner if redis.call('GET',index) ~= ARGV[2] then return nil end "
                    + "redis.call('DEL',KEYS[1],index) return value",
            String.class);
    private static final DefaultRedisScript<String> FIND = new DefaultRedisScript<>(
            "local value=redis.call('GET',KEYS[1]) if not value then return nil end "
                    + "local owner=string.match(value,'^([^|]+)|') if not owner then return nil end "
                    + "if redis.call('GET',ARGV[1]..owner) ~= ARGV[2] then return nil end return value",
            String.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>(
            "local old=redis.call('GET',KEYS[1]) "
                    + "if old then redis.call('DEL',ARGV[1]..old) end "
                    + "redis.call('DEL',KEYS[1]) return 1",
            Long.class);
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    RedisBindingTokens(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(UUID userId, String challengeId, Duration ttl, boolean replaceExisting) {
        if (ttl == null || ttl.toSeconds() <= 0) {
            throw new IllegalArgumentException("Binding token TTL must be positive");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.execute(
                ISSUE,
                List.of(USER_PREFIX + userId),
                PREFIX,
                digest(token),
                userId + "|" + (challengeId == null ? "" : challengeId) + "|" + replaceExisting,
                String.valueOf(ttl.toSeconds()));
        return token;
    }

    @Override
    public Optional<BindingRequest> find(String token) {
        if (!valid(token)) {
            return Optional.empty();
        }
        String hash = digest(token);
        return request(redis.execute(FIND, List.of(PREFIX + hash), USER_PREFIX, hash));
    }

    @Override
    public Optional<BindingRequest> consume(String token) {
        if (!valid(token)) {
            return Optional.empty();
        }
        String hash = digest(token);
        return request(redis.execute(CONSUME, List.of(PREFIX + hash), USER_PREFIX, hash));
    }

    private Optional<BindingRequest> request(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split("\\|", -1);
        return Optional.of(new BindingRequest(
                UUID.fromString(fields[0]),
                fields.length > 1 ? fields[1] : "",
                fields.length > 2 && Boolean.parseBoolean(fields[2])));
    }

    private boolean valid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    @Override
    public void invalidate(UUID userId) {
        redis.execute(INVALIDATE, List.of(USER_PREFIX + userId), PREFIX);
    }

    private String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
