package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.BlockedDevice;
import dev.workflowcrm.auth.domain.ConfirmationMode;
import dev.workflowcrm.auth.domain.DeviceTrust;
import dev.workflowcrm.auth.domain.SessionMetadata;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class RedisDeviceTrust implements DeviceTrust {
    private static final String TRUSTED = "auth:trusted-device:";
    private static final String BLOCKED = "auth:blocked-device:";
    private static final String FIRST_LOGIN = "auth:first-login-device:";
    private static final String DETAILS = "auth:blocked-device-details:";
    private static final DefaultRedisScript<Long> BLOCK = script("block-device.lua", Long.class);
    private static final DefaultRedisScript<List> READ_BLOCK = script("read-blocked-device.lua", List.class);
    private static final DefaultRedisScript<Long> UNBLOCK = script("unblock-device.lua", Long.class);

    private final StringRedisTemplate redis;
    private final DeviceSecurityProperties properties;
    private final Clock clock;

    RedisDeviceTrust(StringRedisTemplate redis, DeviceSecurityProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean trusted(UUID userId, String deviceId) {
        return valid(deviceId)
                && !blocked(userId, deviceId)
                && Boolean.TRUE.equals(redis.hasKey(key(TRUSTED, userId, deviceId)));
    }

    @Override
    public boolean blocked(UUID userId, String deviceId) {
        return valid(deviceId) && Boolean.TRUE.equals(redis.hasKey(key(BLOCKED, userId, deviceId)));
    }

    @Override
    public void trust(UUID userId, String deviceId) {
        if (!valid(deviceId) || blocked(userId, deviceId)) {
            throw new IllegalStateException("Device cannot be trusted");
        }
        redis.opsForValue().set(key(TRUSTED, userId, deviceId), "1", properties.trustDuration());
    }

    @Override
    public void trustFromInvitation(UUID userId, String deviceId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        redis.delete(
                                java.util.List.of(key(TRUSTED, userId, deviceId), key(FIRST_LOGIN, userId, deviceId)));
                    }
                }
            });
        }
        trust(userId, deviceId);
        if (properties.confirmationMode() == ConfirmationMode.EVERY_LOGIN) {
            redis.opsForValue().set(key(FIRST_LOGIN, userId, deviceId), "1", properties.trustDuration());
        }
    }

    @Override
    public boolean consumeFirstLogin(UUID userId, String deviceId) {
        return valid(deviceId) && "1".equals(redis.opsForValue().getAndDelete(key(FIRST_LOGIN, userId, deviceId)));
    }

    @Override
    public void block(UUID userId, String deviceId) {
        block(userId, new SessionMetadata(deviceId, "", ""));
    }

    @Override
    public void block(UUID userId, SessionMetadata metadata) {
        if (!valid(metadata.deviceId())) {
            throw new IllegalArgumentException("Invalid device identifier");
        }
        redis.execute(
                BLOCK,
                blockKeys(userId, metadata.deviceId()),
                UUID.randomUUID().toString(),
                metadata.userAgent(),
                metadata.ip(),
                clock.instant().toString(),
                Long.toString(Duration.ofDays(365).toMillis()));
    }

    @Override
    public List<BlockedDevice> blockedDevices(UUID userId) {
        String prefix = BLOCKED + userId + ":";
        List<BlockedDevice> result = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        try (Cursor<String> keys = redis.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
            while (keys.hasNext()) {
                String deviceId = keys.next().substring(prefix.length());
                if (!valid(deviceId) || !visited.add(deviceId)) {
                    continue;
                }
                List<?> record = redis.execute(
                        READ_BLOCK,
                        List.of(key(BLOCKED, userId, deviceId), key(DETAILS, userId, deviceId)),
                        UUID.randomUUID().toString());
                if (record == null || record.isEmpty()) {
                    continue;
                }
                String blockedAt = record.get(3).toString();
                result.add(new BlockedDevice(
                        UUID.fromString(record.get(0).toString()),
                        deviceId,
                        record.get(1).toString(),
                        record.get(2).toString(),
                        blockedAt.isBlank() ? null : Instant.parse(blockedAt)));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean unblock(UUID userId, BlockedDevice device) {
        if (!valid(device.deviceId())) {
            throw new IllegalArgumentException("Invalid device identifier");
        }
        return Long.valueOf(1)
                .equals(redis.execute(
                        UNBLOCK,
                        blockKeys(userId, device.deviceId()),
                        device.id().toString()));
    }

    private List<String> blockKeys(UUID userId, String deviceId) {
        return List.of(
                key(BLOCKED, userId, deviceId),
                key(DETAILS, userId, deviceId),
                key(TRUSTED, userId, deviceId),
                key(FIRST_LOGIN, userId, deviceId));
    }

    private boolean valid(String deviceId) {
        return deviceId != null && deviceId.matches("[a-f0-9]{64}");
    }

    private static <T> DefaultRedisScript<T> script(String fileName, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/" + fileName));
        script.setResultType(resultType);
        return script;
    }

    private String key(String prefix, UUID userId, String deviceId) {
        return prefix + userId + ":" + deviceId;
    }
}
