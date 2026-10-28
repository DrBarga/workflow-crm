package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.audit.api.TelegramBindingAudit;
import dev.workflowcrm.users.api.TelegramAccountAlreadyBoundException;
import dev.workflowcrm.users.api.TelegramBinding;
import dev.workflowcrm.users.api.TelegramBindingState;
import dev.workflowcrm.users.api.TelegramChatInUseException;
import dev.workflowcrm.users.domain.User;
import dev.workflowcrm.users.domain.UserStatus;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaTelegramBinding implements TelegramBinding {
    private final UserRepository users;
    private final TelegramBindingAudit audit;

    JpaTelegramBinding(UserRepository users, TelegramBindingAudit audit) {
        this.users = users;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> email(UUID userId) {
        return users.findById(userId).map(User::getEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean belongsToAnotherUser(UUID userId, long chatId) {
        return users.existsByTelegramChatIdAndIdNot(chatId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong chatId(UUID userId) {
        return users.findById(userId)
                .map(User::getTelegramChatId)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    @Override
    @Transactional
    public void bind(UUID userId, long chatId) {
        save(userId, chatId, false);
    }

    @Override
    @Transactional
    public void replace(UUID userId, long chatId) {
        save(userId, chatId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public TelegramBindingState state(UUID userId) {
        return new TelegramBindingState(
                chatId(userId).isPresent(), audit.latestEvent(userId).orElse(null));
    }

    private void save(UUID userId, long chatId, boolean replace) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("Invalid Telegram chat");
        }
        User user = users.findForUpdate(userId).orElseThrow();
        if (user.getStatus() != UserStatus.ACTIVE) throw new IllegalStateException("User is disabled");
        Long previousChat = user.getTelegramChatId();
        if (!replace && previousChat != null) {
            throw new TelegramAccountAlreadyBoundException();
        }
        if (belongsToAnotherUser(userId, chatId)) {
            throw new TelegramChatInUseException();
        }
        user.bindTelegram(chatId);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new TelegramChatInUseException();
        }
        if (previousChat != null && previousChat != chatId)
            audit.record(userId, previousChat, TelegramBindingAudit.Action.UNBOUND);
        audit.record(
                userId,
                chatId,
                audit.hasPreviousBinding(userId)
                        ? TelegramBindingAudit.Action.REBOUND
                        : TelegramBindingAudit.Action.BOUND);
    }

    @Override
    @Transactional
    public void unbind(UUID userId) {
        User user = users.findForUpdate(userId).orElseThrow();
        Long chatId = user.getTelegramChatId();
        user.unbindTelegram();
        if (chatId != null) {
            audit.record(userId, chatId, TelegramBindingAudit.Action.UNBOUND);
        }
    }
}
