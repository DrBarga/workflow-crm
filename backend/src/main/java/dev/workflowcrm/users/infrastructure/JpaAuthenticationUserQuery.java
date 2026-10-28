package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.domain.User;
import dev.workflowcrm.users.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaAuthenticationUserQuery implements AuthenticationUserQuery {

    private final UserRepository userRepository;
    private final JdbcUserRegions regions;

    JpaAuthenticationUserQuery(UserRepository userRepository, JdbcUserRegions regions) {
        this.userRepository = userRepository;
        this.regions = regions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationUser> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(this::toAuthenticationUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationUser> findById(UUID id) {
        return userRepository.findById(id).map(this::toAuthenticationUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> email(UUID id) {
        return userRepository.findById(id).map(User::getEmail);
    }

    private AuthenticationUser toAuthenticationUser(User user) {
        var assigned = regions.assigned(user.getId());
        return new AuthenticationUser(
                user.getId(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getRole().getCode().name(),
                assigned.isEmpty() ? null : assigned.getFirst(),
                user.getStatus() == UserStatus.ACTIVE,
                assigned);
    }
}
