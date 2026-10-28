package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.objects.deletion.worker-enabled=true", "app.objects.deletion.poll-delay-ms=50"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObjectDeletionWorkerIT extends ObjectDeletionTestSupport {
    @Test
    void scheduledWorkerCompletesAcceptedDeletionWithoutAnotherRequest() throws Exception {
        UUID object = add("Scheduled", manager, region);
        String bucket = bucket();
        file(object, null, bucket, "file");
        UUID operation = UUID.fromString(accepted(object).path("id").asText());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(count("objects")).isZero());
        operation(admin, operation)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));
        assertThat(stored(bucket)).isZero();
        assertThat(count("object_deletion_audit")).isEqualTo(1);
    }
}
