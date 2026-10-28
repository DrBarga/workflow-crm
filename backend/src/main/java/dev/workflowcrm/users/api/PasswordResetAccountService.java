package dev.workflowcrm.users.api;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetAccountService {
    Optional<PasswordResetAccount> lockEligibleByEmail(String email);

    Optional<PasswordResetAccount> changePassword(UUID userId, String password);
}
