package dev.workflowcrm.auth.domain;

public record LoginStart(LoginResult completed, LoginChallenge challenge) {
    public static LoginStart completed(LoginResult result) {
        return new LoginStart(result, null);
    }

    public static LoginStart pending(LoginChallenge challenge) {
        return new LoginStart(null, challenge);
    }
}
