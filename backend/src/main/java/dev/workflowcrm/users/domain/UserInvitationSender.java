package dev.workflowcrm.users.domain;

public interface UserInvitationSender {
    void sendPasswordSetup(String email, String fullName, String token);
}
