package dev.workflowcrm.auth.domain;

public interface AuthenticationCookieFactory {

    String create(String token);

    String clear();
}
