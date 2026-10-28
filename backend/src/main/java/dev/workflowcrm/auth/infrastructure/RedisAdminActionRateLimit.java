package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.AdminActionRateLimit;
import dev.workflowcrm.shared.error.RequestRateLimitException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AdminSecurityProperties.class)
class RedisAdminActionRateLimit implements AdminActionRateLimit {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end; return count;",
            Long.class);
    private final StringRedisTemplate redis;
    private final AdminSecurityProperties properties;

    RedisAdminActionRateLimit(StringRedisTemplate redis, AdminSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void reserve(UUID administratorId) {
        Long count = redis.execute(
                RESERVE,
                List.of("auth:admin-unlock-rate:" + administratorId),
                Long.toString(properties.rateWindow().toMillis()));
        if (count == null) {
            throw new IllegalStateException("Administrative rate limit is unavailable");
        }
        if (count > properties.maxActions()) {
            throw new RequestRateLimitException("Wait before another security administration request");
        }
    }
}
