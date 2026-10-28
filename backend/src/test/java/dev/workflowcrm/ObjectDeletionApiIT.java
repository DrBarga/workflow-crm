package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.objects.domain.ObjectDeletionService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class ObjectDeletionApiIT extends ObjectDeletionTestSupport {
    @Autowired
    ObjectDeletionService deletions;

    @Test
    void onlyActiveAdministratorCanRequestOrReadDeletion() throws Exception {
        UUID object = add("Delete", manager, region);
        for (var actor : List.of(manager, lead, director, contract, ceo))
            remove(actor, object).andExpect(status().isForbidden());
        remove(admin, UUID.randomUUID()).andExpect(status().isNotFound());
        var accepted = accepted(object);
        UUID operation = UUID.fromString(accepted.path("id").asText());
        for (var actor : List.of(manager, lead, director, contract, ceo))
            operation(actor, operation).andExpect(status().isForbidden());
        operation(admin, operation).andExpect(status().isOk());
        operation(admin, UUID.randomUUID()).andExpect(status().isNotFound());
        jdbc.update("update users set status='DISABLED' where id=?", admin);
        remove(admin, object).andExpect(status().isForbidden());
        operation(admin, operation).andExpect(status().isForbidden());
        assertThat(processor.processNext()).isTrue();
        assertThat(count("objects")).isZero();
    }

    @Test
    void cookieAuthenticationAndCsrfAreRequired() throws Exception {
        UUID object = add("CSRF", manager, region);
        mvc.perform(delete("/api/objects/" + object)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/objects/" + object).cookie(cookie(admin))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/objects/" + object).cookie(csrf).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
        assertThat(count("object_deletion_operations")).isZero();
    }

    @Test
    void pendingOperationIsIdempotentAndFinishedDeletionReturns404() throws Exception {
        UUID object = add("Delete", manager, region);
        var accepted = accepted(object);
        mvc.perform(delete("/api/objects/" + object.toString().toUpperCase(Locale.ROOT))
                        .cookie(cookie(admin), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isAccepted());
        remove(admin, object)
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                                "Location",
                                "/api/object-deletions/" + accepted.path("id").asText()))
                .andExpect(jsonPath("$.id").value(accepted.path("id").asText()));
        assertThat(count("object_deletion_operations")).isEqualTo(1);
        assertThat(processor.processNext()).isTrue();
        assertThat(processor.processNext()).isFalse();
        operation(admin, UUID.fromString(accepted.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));
        remove(admin, object).andExpect(status().isNotFound());
        mvc.perform(get("/api/objects/" + object).cookie(cookie(admin))).andExpect(status().isNotFound());
    }

    @Test
    void concurrentRequestsCreateOneDurableOperation() throws Exception {
        UUID object = add("Concurrent", manager, region);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<UUID> request = () -> {
                gate.await();
                return deletions.request(admin, object).id();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(count("object_deletion_operations")).isEqualTo(1);
    }

    @Test
    void pendingObjectsAreHiddenAndCannotBeChangedOrRebound() throws Exception {
        UUID object = add("Frozen", manager, region), other = add("Other", manager, region);
        UUID task = task(object, "NEW");
        UUID file = file(object, null, bucket(), "file");
        accepted(object);
        assertThat(names(page(admin, Map.of()))).containsExactly("Other");
        mvc.perform(get("/api/objects/" + object).cookie(cookie(admin))).andExpect(status().isConflict());
        mvc.perform(get("/api/objects/" + object + "/status-requests/" + UUID.randomUUID())
                        .cookie(cookie(admin)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/objects/" + object)
                        .cookie(cookie(admin), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"version\":0,\"name\":\"changed\"}"))
                .andExpect(status().isConflict());
        for (String sql : List.of(
                "update objects set name='changed' where id='" + object + "'",
                "update tasks set object_id='" + other + "' where id='" + task + "'",
                "update files set object_id='" + other + "' where id='" + file + "'",
                "insert into object_comments(object_id,author_id,body) values('" + object + "','" + manager
                        + "','late')"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void creationIdempotencyKeyCannotResurrectDeletedObject() throws Exception {
        String key = key();
        var body = body();
        var created = json.readTree(create(manager, key, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        UUID object = UUID.fromString(created.path("id").asText());
        accepted(object);
        processor.processNext();
        create(manager, key, body).andExpect(status().isNotFound());
        assertThat(count("object_creation_requests")).isEqualTo(1);
        assertThat(count("objects")).isZero();
    }
}
