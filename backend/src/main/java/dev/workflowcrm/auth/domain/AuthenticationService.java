package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUser;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AuthenticationUserQuery userQuery;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptTracker attemptTracker;
    private final TokenIssuer tokenIssuer;
    private final RefreshSessionStore sessionStore;

    public AuthenticationService(
            AuthenticationUserQuery userQuery,
            PasswordEncoder passwordEncoder,
            LoginAttemptTracker attemptTracker,
            TokenIssuer tokenIssuer,
            RefreshSessionStore sessionStore) {
        this.userQuery = userQuery;
        this.passwordEncoder = passwordEncoder;
        this.attemptTracker = attemptTracker;
        this.tokenIssuer = tokenIssuer;
        this.sessionStore = sessionStore;
    }

    public LoginResult authenticate(LoginCredentials credentials) {
        return authenticate(credentials, SessionMetadata.EMPTY);
    }

    public LoginResult authenticate(LoginCredentials credentials, SessionMetadata metadata) {
        return issue(verify(credentials, metadata));
    }

    public VerifiedLogin verify(LoginCredentials credentials, SessionMetadata metadata) {
        String email = normalizeEmail(credentials.email());

        if (attemptTracker.isBlocked(email)) {
            throw new LoginTemporarilyBlockedException();
        }

        AuthenticationUser user = userQuery.findByEmail(email).orElse(null);
        String storedHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();

        if (!passwordEncoder.matches(credentials.password(), storedHash) || user == null) {
            attemptTracker.registerFailure(email);
            throw new InvalidCredentialsException();
        }

        if (!user.active()) {
            throw new DisabledUserException();
        }

        attemptTracker.clear(email);
        return new VerifiedLogin(user, metadata, email);
    }

    public LoginResult issue(VerifiedLogin login) {
        AuthenticationUser user = login.user();
        IssuedRefreshToken refresh = sessionStore.create(user.id(), login.metadata());
        String token = tokenIssuer.issue(user, refresh.sessionId());
        return new LoginResult(user.id(), user.role(), user.regionId(), user.fullName(), token, refresh.value());
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
