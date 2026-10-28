package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.auth.domain.TelegramBindingDecision;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ProfileTelegramConcurrencyIT extends ProfileTelegramTestSupport {
    @Test
    void sameTokenCannotBindTwiceConcurrently() throws Exception {
        String token = link(user);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<TelegramBindingDecision> task = () -> {
                gate.await();
                return flow.confirmTelegramBinding(token, 50401);
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            gate.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(TelegramBindingDecision.BOUND, TelegramBindingDecision.EXPIRED);
        }
        assertThat(events(user)).isEqualTo(1);
    }

    @Test
    void concurrentOwnersCannotClaimSameChat() throws Exception {
        binding.bind(user, 50402);
        binding.bind(other, 50403);
        String first = link(user), second = link(other);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> {
                gate.await();
                return flow.confirmTelegramBinding(first, 50404);
            });
            var b = pool.submit(() -> {
                gate.await();
                return flow.confirmTelegramBinding(second, 50404);
            });
            gate.countDown();
            var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(results)
                    .containsExactlyInAnyOrder(TelegramBindingDecision.BOUND, TelegramBindingDecision.CHAT_IN_USE);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where telegram_chat_id=50404", Integer.class))
                .isEqualTo(1);
        assertThat(events(user) + events(other)).isEqualTo(4);
        assertThat(List.of(
                        binding.chatId(user).orElseThrow(),
                        binding.chatId(other).orElseThrow()))
                .contains(50404L)
                .containsAnyOf(50402L, 50403L);
    }

    @Test
    void auditFailureRollsBackChatReplacement() {
        binding.bind(user, 50405);
        jdbc.execute(
                "create function profile_reject_audit() returns trigger language plpgsql as $$ begin raise exception 'audit unavailable'; end; $$");
        jdbc.execute(
                "create trigger profile_reject_audit before insert on telegram_binding_events for each row execute function profile_reject_audit()");
        try {
            assertThatThrownBy(() -> binding.replace(user, 50406)).isInstanceOf(RuntimeException.class);
            assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50405);
            assertThat(events(user)).isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger profile_reject_audit on telegram_binding_events");
            jdbc.execute("drop function profile_reject_audit()");
        }
    }
}
