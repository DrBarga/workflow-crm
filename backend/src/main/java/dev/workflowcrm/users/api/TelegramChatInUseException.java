package dev.workflowcrm.users.api;

public class TelegramChatInUseException extends RuntimeException {
    public TelegramChatInUseException() {
        super("Telegram chat is already linked to another account");
    }
}
