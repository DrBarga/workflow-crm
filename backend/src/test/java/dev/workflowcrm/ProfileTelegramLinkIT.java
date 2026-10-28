package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProfileTelegramLinkIT extends ProfileTelegramTestSupport {
    @Test
    void linkIsPrivateSingleUseAndExpiresInTenMinutes() throws Exception {
        Instant before = Instant.now();
        var response = linkResponse(user);
        String value = response.path("link").asText();
        assertThat(value).matches("https://t.me/workflow_test_bot\\?start=[A-Za-z0-9_-]{43}");
        assertThat(response.path("expiresInSeconds").asLong()).isEqualTo(600);
        assertThat(Instant.parse(response.path("expiresAt").asText()))
                .isBetween(before.plusSeconds(599), Instant.now().plusSeconds(601));
        String token = token(value);
        String key = redis.keys("auth:telegram-binding:*").stream()
                .filter(candidate -> redis.opsForValue().get(candidate).startsWith(user.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(redis.getExpire(key)).isBetween(598L, 600L);
        assertThat(redis.opsForValue().get(key)).doesNotContain(token);
        assertThat(tokens.find(token).orElseThrow().replaceExisting()).isTrue();
        var oldRevision = profile(user).path("telegram").path("revision");
        start("/start " + token, 50101);
        verify(bot).sendMessage(eq(50101L), contains(user + "@example.com"), argThat(buttons -> buttons.size() == 2));
        assertThat(binding.chatId(user)).isEmpty();
        decide(token, 50101, true);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50101);
        assertThat(profile(user).path("telegram").path("linked").asBoolean()).isTrue();
        assertThat(profile(user).path("telegram").path("revision")).isNotEqualTo(oldRevision);
        verify(bot).sendMessage(eq(50101L), contains("Telegram привязан.\nEmail: " + user), eq(List.of()));
        decide(token, 50101, true);
        assertThat(events(user)).isEqualTo(1);
        verify(bot).sendMessage(eq(50101L), contains("срок её действия истёк"), eq(List.of()));
    }

    @Test
    void newLinkInvalidatesPreviousAndRejectDoesNotBind() throws Exception {
        String first = link(user), second = link(user);
        assertThat(tokens.find(first)).isEmpty();
        decide(first, 50102, true);
        assertThat(binding.chatId(user)).isEmpty();
        decide(second, 50102, false);
        assertThat(tokens.find(second)).isEmpty();
        assertThat(binding.chatId(user)).isEmpty();
        assertThat(events(user)).isZero();
    }

    @Test
    void expiredAndMissingTokensExplainHowToGetNewLink() throws Exception {
        String token = tokens.issue(user, "", Duration.ofMinutes(10), true);
        tokens.invalidate(user);
        start("/start " + token, 50103);
        verify(bot).sendMessage(eq(50103L), contains("Запросите новую"), eq(List.of()));
        start("/start", 50103);
        verify(bot).sendMessage(eq(50103L), contains("запросите ссылку"), eq(List.of()));
        String live = link(user);
        start("/start@workflow_test_bot\t" + live, 50103);
        verify(bot).sendMessage(eq(50103L), contains("Email: " + user), argThat(buttons -> buttons.size() == 2));
    }

    @Test
    void redisExpiryPreventsBinding() throws Exception {
        String token = link(user);
        for (String key : redis.keys("auth:telegram-binding:*"))
            if (redis.opsForValue().get(key).startsWith(user.toString())) redis.delete(key);
        decide(token, 50104, true);
        assertThat(binding.chatId(user)).isEmpty();
        assertThat(events(user)).isZero();
    }
}
