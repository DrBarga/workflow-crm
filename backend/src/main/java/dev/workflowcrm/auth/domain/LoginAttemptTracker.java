package dev.workflowcrm.auth.domain;

public interface LoginAttemptTracker {

    boolean isBlocked(String email);

    void registerFailure(String email);

    void clear(String email);

    LoginLock lock(String email);

    boolean unlock(String email);
}
