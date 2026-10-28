package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectCreationIdempotencyIT extends ObjectApiTestSupport {
    @Test
    void retryReturnsSameObjectAndDoesNotRepeatAuditOrGeocoding() throws Exception {
        String key = key();
        var first = create(manager, key, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var second = create(manager, key, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
        verifyNoInteractions(geocoder);
    }

    @Test
    void sameKeyDifferentBodyIsConflictButKeyIsScopedToCreator() throws Exception {
        String key = key();
        create(manager, key, body()).andExpect(status().isCreated());
        var changed = body();
        changed.put("name", "Changed");
        create(manager, key, changed).andExpect(status().isConflict());
        var confirmed = body();
        confirmed.put("confirmDuplicates", true);
        create(colleague, key, confirmed).andExpect(status().isCreated());
        assertThat(count("objects")).isEqualTo(2);
    }

    @Test
    void noKeyAndBlankKeyNeverCreateAnObjectIncludingDoubleSubmit() throws Exception {
        create(manager, null, body()).andExpect(status().isUnprocessableEntity());
        create(manager, null, body()).andExpect(status().isUnprocessableEntity());
        create(manager, "", body()).andExpect(status().isUnprocessableEntity());
        assertThat(count("objects")).isZero();
    }

    @Test
    void concurrentRequestsCommitOneObjectAndReturnSameId() throws Exception {
        String key = key();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return create(manager, key, body())
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                    }))
                    .toList();
            start.countDown();
            String first = futures.getFirst().get(30, TimeUnit.SECONDS);
            for (var future : futures)
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(first);
        }
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(count("object_creation_requests")).isEqualTo(1);
    }

    @Test
    void regionChangeRevokesAccessToPreviousResponse() throws Exception {
        String key = key();
        create(manager, key, body()).andExpect(status().isCreated());
        UserRegionTestData.replace(jdbc, manager, otherRegion);
        create(manager, key, body()).andExpect(status().isForbidden());
    }

    @Test
    void reassignmentRevokesPreviousManagersReplayAccess() throws Exception {
        String key = key();
        create(manager, key, body()).andExpect(status().isCreated());
        jdbc.update("update objects set manager_id=?", colleague);
        create(manager, key, body()).andExpect(status().isForbidden());
    }
}
