package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.TokenRevocationStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisTokenRevocationStore implements TokenRevocationStore {

    private static final String KEY_PREFIX = "auth:revoked-token:";
    private static final DefaultRedisScript<Long> REVOKE =
            new DefaultRedisScript<>("redis.call('SET', KEYS[1], '1', 'PXAT', ARGV[1]); return 1", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    RedisTokenRevocationStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void revoke(String tokenId, Instant expiresAt) {
        if (!expiresAt.isAfter(clock.instant())) {
            return;
        }
        redisTemplate.execute(REVOKE, List.of(KEY_PREFIX + tokenId), Long.toString(expiresAt.toEpochMilli()));
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
    }
}
