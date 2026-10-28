package dev.workflowcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.TokenPair;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshReplayCodecTest {
    private final RefreshReplayCodec codec = new RefreshReplayCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void recoversTheExactPairOnlyWithThePresentedToken() {
        var pair = new TokenPair("signed-access", "new-refresh", Instant.parse("2026-09-18T12:00:00Z"));
        String encrypted = codec.encrypt("old-random-refresh-token", pair);
        assertThat(encrypted).doesNotContain(pair.accessToken(), pair.refreshToken(), "old-random-refresh-token");
        assertThat(codec.decrypt("old-random-refresh-token", encrypted)).isEqualTo(pair);
        assertThatThrownBy(() -> codec.decrypt("another-refresh-token", encrypted))
                .isInstanceOf(IllegalStateException.class);
        assertThat(codec.encrypt("old-random-refresh-token", pair)).isNotEqualTo(encrypted);
    }

    @Test
    void rejectsCorruptAndTruncatedPayloads() {
        for (String invalid : java.util.List.of("invalid!", "YQ==", "")) {
            assertThatThrownBy(() -> codec.decrypt("token", invalid)).isInstanceOf(IllegalStateException.class);
        }
    }
}
