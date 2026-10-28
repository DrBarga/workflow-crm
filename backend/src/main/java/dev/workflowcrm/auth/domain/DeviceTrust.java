package dev.workflowcrm.auth.domain;

import java.util.List;
import java.util.UUID;

public interface DeviceTrust {
    boolean trusted(UUID userId, String deviceId);

    boolean blocked(UUID userId, String deviceId);

    void trust(UUID userId, String deviceId);

    void trustFromInvitation(UUID userId, String deviceId);

    boolean consumeFirstLogin(UUID userId, String deviceId);

    void block(UUID userId, String deviceId);

    void block(UUID userId, SessionMetadata metadata);

    List<BlockedDevice> blockedDevices(UUID userId);

    boolean unblock(UUID userId, BlockedDevice device);
}
