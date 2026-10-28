package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.InitialCredential;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class RandomInitialCredential implements InitialCredential {
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    RandomInitialCredential(PasswordEncoder passwords) {
        this.passwords = passwords;
    }

    public String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
