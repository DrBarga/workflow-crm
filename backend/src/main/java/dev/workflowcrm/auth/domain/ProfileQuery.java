package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import dev.workflowcrm.users.api.TelegramBinding;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileQuery {
    private final AuthenticationUserQuery users;
    private final TelegramBinding binding;

    public ProfileQuery(AuthenticationUserQuery users, TelegramBinding binding) {
        this.users = users;
        this.binding = binding;
    }

    public CurrentProfile current(UUID id) {
        var user = users.findById(id)
                .filter(candidate -> candidate.active())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled"));
        String email = users.email(id).orElseThrow();
        return new CurrentProfile(id, email, user.fullName(), user.role(), user.regionId(), binding.state(id));
    }
}
