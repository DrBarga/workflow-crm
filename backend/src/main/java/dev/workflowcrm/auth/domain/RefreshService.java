package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUserQuery;
import org.springframework.stereotype.Service;

@Service
public class RefreshService {

    private final RefreshSessionStore sessionStore;
    private final AuthenticationUserQuery userQuery;
    private final TokenIssuer tokenIssuer;

    public RefreshService(
            RefreshSessionStore sessionStore, AuthenticationUserQuery userQuery, TokenIssuer tokenIssuer) {
        this.sessionStore = sessionStore;
        this.userQuery = userQuery;
        this.tokenIssuer = tokenIssuer;
    }

    public TokenPair refresh(String refreshToken) {
        RefreshSession session = sessionStore.findByToken(refreshToken).orElseThrow(InvalidRefreshTokenException::new);
        var user = userQuery.findById(session.userId()).filter(candidate -> candidate.active());
        if (user.isEmpty()) {
            sessionStore.revoke(session.id());
            throw new InvalidRefreshTokenException();
        }
        return sessionStore.rotate(refreshToken, session, tokenIssuer.issue(user.get(), session.id()));
    }
}
