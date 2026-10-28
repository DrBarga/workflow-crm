package dev.workflowcrm.users.api;

public class TelegramAccountAlreadyBoundException extends RuntimeException {
    public TelegramAccountAlreadyBoundException() {
        super("Account already has a Telegram binding");
    }
}
