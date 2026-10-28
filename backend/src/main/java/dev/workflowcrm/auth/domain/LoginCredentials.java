package dev.workflowcrm.auth.domain;

public record LoginCredentials(String email, String password) {

    @Override
    public String toString() {
        return "LoginCredentials[credentials redacted]";
    }
}
