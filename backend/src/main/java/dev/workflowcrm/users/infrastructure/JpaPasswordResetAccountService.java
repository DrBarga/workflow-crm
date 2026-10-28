package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.shared.security.PasswordPolicy;
import dev.workflowcrm.users.api.PasswordResetAccount;
import dev.workflowcrm.users.api.PasswordResetAccountService;
import dev.workflowcrm.users.domain.User;
import dev.workflowcrm.users.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class JpaPasswordResetAccountService implements PasswordResetAccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    JpaPasswordResetAccountService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public Optional<PasswordResetAccount> lockEligibleByEmail(String email) {
        return users.findByEmailIgnoreCaseForUpdate(email)
                .filter(JpaPasswordResetAccountService::eligible)
                .map(this::view);
    }

    @Override
    public Optional<PasswordResetAccount> changePassword(UUID userId, String password) {
        return users.findForUpdate(userId)
                .filter(JpaPasswordResetAccountService::eligible)
                .map(user -> {
                    PasswordPolicy.validate(password, user.getEmail(), user.getFullName());
                    user.changePassword(passwords.encode(password));
                    users.saveAndFlush(user);
                    return view(user);
                });
    }

    private PasswordResetAccount view(User user) {
        return new PasswordResetAccount(user.getId(), user.getEmail(), user.getFullName());
    }

    private static boolean eligible(User user) {
        return user.getStatus() == UserStatus.ACTIVE && !user.isPasswordSetupRequired();
    }
}
