package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.LoginAttemptTracker;
import dev.workflowcrm.auth.domain.LoginLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisLoginAttemptTracker implements LoginAttemptTracker {

    private static final String KEY_PREFIX = "auth:login-attempts:";
    private static final DefaultRedisScript<Long> REGISTER_FAILURE_SCRIPT = new DefaultRedisScript<>(
            "local attempts = redis.call('INCR', KEYS[1]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[1]); "
                    + "return attempts;",
            Long.class);
    private static final DefaultRedisScript<List> READ_LOCK = new DefaultRedisScript<>(
            "local count = redis.call('GET', KEYS[1]); if not count then return {'0', '-2'} end; return {count, tostring(redis.call('PTTL', KEYS[1]))};",
            List.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "local count = tonumber(redis.call('GET', KEYS[1]) or '0'); if count < tonumber(ARGV[1]) then return 0 end; return redis.call('DEL', KEYS[1]);",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LoginProtectionProperties properties;
    private final Clock clock;

    RedisLoginAttemptTracker(StringRedisTemplate redisTemplate, LoginProtectionProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean isBlocked(String email) {
        String attempts = redisTemplate.opsForValue().get(key(email));
        return attempts != null && Long.parseLong(attempts) >= properties.maxAttempts();
    }

    @Override
    public void registerFailure(String email) {
        redisTemplate.execute(
                REGISTER_FAILURE_SCRIPT,
                List.of(key(email)),
                String.valueOf(properties.lockDuration().toMillis()));
    }

    @Override
    public void clear(String email) {
        redisTemplate.delete(key(email));
    }

    @Override
    public LoginLock lock(String email) {
        List<?> state = redisTemplate.execute(READ_LOCK, List.of(key(email)));
        if (state == null || state.size() != 2) {
            throw new IllegalStateException("Login lock state is unavailable");
        }
        int attempts = Integer.parseInt(state.get(0).toString());
        long ttl = Long.parseLong(state.get(1).toString());
        boolean blocked = attempts >= properties.maxAttempts() && ttl != -2 && ttl != 0;
        return new LoginLock(
                blocked,
                attempts,
                properties.maxAttempts(),
                properties.lockDuration().toSeconds(),
                blocked && ttl > 0 ? clock.instant().plusMillis(ttl) : null);
    }

    @Override
    public boolean unlock(String email) {
        return Long.valueOf(1)
                .equals(redisTemplate.execute(UNLOCK, List.of(key(email)), Integer.toString(properties.maxAttempts())));
    }

    private String key(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
