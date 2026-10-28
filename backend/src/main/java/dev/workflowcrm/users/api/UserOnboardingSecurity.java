package dev.workflowcrm.users.api;

import java.util.UUID;

public interface UserOnboardingSecurity {
    String deviceCookieName();

    String prepareInvitationDevice(String presentedCookie);

    String trustInvitationDevice(UUID userId, String presentedCookie);
}
