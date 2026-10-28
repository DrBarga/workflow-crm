package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.audit.api.PasswordSetupAudit;
import dev.workflowcrm.auth.domain.DeviceTrust;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.UserOnboardingSecurity;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ProvisionedUserOnboarding implements UserOnboardingSecurity {
    private final AuthenticationUserQuery users;
    private final DeviceCookie deviceCookie;
    private final DeviceTrust devices;
    private final PasswordSetupAudit audit;

    ProvisionedUserOnboarding(
            AuthenticationUserQuery users, DeviceCookie deviceCookie, DeviceTrust devices, PasswordSetupAudit audit) {
        this.users = users;
        this.deviceCookie = deviceCookie;
        this.devices = devices;
        this.audit = audit;
    }

    @Override
    public String deviceCookieName() {
        return deviceCookie.name();
    }

    @Override
    public String prepareInvitationDevice(String presentedCookie) {
        return deviceCookie.create(deviceValue(presentedCookie));
    }

    @Override
    public String trustInvitationDevice(UUID userId, String presentedCookie) {
        requireUser(userId);
        String cookieValue = deviceValue(presentedCookie);
        String deviceId = deviceCookie.deviceId(cookieValue);
        devices.trustFromInvitation(userId, deviceId);
        audit.record(userId, deviceId);
        return deviceCookie.create(cookieValue);
    }

    private String deviceValue(String presentedCookie) {
        return deviceCookie.deviceId(presentedCookie).isBlank() ? deviceCookie.newValue() : presentedCookie;
    }

    private void requireUser(UUID userId) {
        users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
