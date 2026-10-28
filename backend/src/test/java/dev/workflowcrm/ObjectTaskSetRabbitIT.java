package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.objects.infrastructure.ObjectTaskSetWorker;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

@TestPropertySource(
        properties = {
            "app.tasks.generation.worker-enabled=true",
            "app.tasks.generation.consumer-enabled=false",
            "app.tasks.generation.initial-delay-ms=3600000",
            "spring.rabbitmq.connection-timeout=1000"
        })
class ObjectTaskSetRabbitIT extends ObjectStatusTasksSupport {
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:4-management-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672);

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry properties) {
        properties.add("spring.rabbitmq.host", RABBIT::getHost);
        properties.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
    }

    @Autowired
    ObjectTaskSetWorker worker;

    @Autowired
    RabbitListenerEndpointRegistry listeners;

    @Autowired
    CachingConnectionFactory connections;

    @Autowired
    @Qualifier("taskSetRabbitTemplate")
    RabbitTemplate broker;

    @AfterEach
    void stopListeners() {
        listeners.stop();
    }

    @Test
    void brokerDeliversStoredJobAndRepeatedMessagesDoNotGenerateAnotherSet() throws Exception {
        var id = submit();
        assertThat(worker.dispatchNext()).isTrue();
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("tasks")).isZero();
        broker.convertAndSend("workflow.task-set-generation", id);
        listeners.start();
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(jobState()).isEqualTo("SUCCEEDED"));
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(state()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void brokerOutageRetainsUnchangedObjectAndJobRecoversAfterReconnect() throws Exception {
        RABBIT.execInContainer("rabbitmqctl", "stop_app");
        connections.resetConnection();
        try {
            submit();
            assertThatThrownBy(worker::dispatchNext).isInstanceOf(AmqpException.class);
            assertThat(jobState()).isEqualTo("QUEUED");
            assertThat(state()).isEqualTo("FOUND");
            assertThat(count("task_sets")).isZero();
            assertThat(count("tasks")).isZero();
        } finally {
            RABBIT.execInContainer("rabbitmqctl", "start_app");
            connections.resetConnection();
        }
        new RabbitAdmin(connections)
                .declareQueue(new org.springframework.amqp.core.Queue("workflow.task-set-generation", true));
        assertThat(worker.dispatchNext()).isTrue();
        listeners.start();
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(jobState()).isEqualTo("SUCCEEDED"));
        assertThat(count("task_sets")).isEqualTo(1);
    }

    private String submit() throws Exception {
        template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        String path = "/api/objects/" + object + "/task-sets";
        var preview = response(send(manager, path + "/preview", body, false).andExpect(status().isOk()));
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        return response(mvc.perform(post(path)
                                .cookie(cookie(manager), csrf)
                                .header("X-XSRF-TOKEN", csrfToken)
                                .header("Idempotency-Key", key())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(body)))
                        .andExpect(status().isAccepted()))
                .path("id")
                .asText();
    }

    private String jobState() {
        return jdbc.queryForObject("select state from object_task_set_jobs where object_id=?", String.class, object);
    }
}
