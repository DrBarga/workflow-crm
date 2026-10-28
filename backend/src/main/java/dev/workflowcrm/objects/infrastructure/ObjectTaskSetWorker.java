package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectTaskSetDelivery;
import dev.workflowcrm.objects.domain.ObjectTaskSetProcessor;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.tasks.generation.worker-enabled", havingValue = "true", matchIfMissing = true)
public class ObjectTaskSetWorker {
    static final String QUEUE = "workflow.task-set-generation";
    private static final Logger log = LoggerFactory.getLogger(ObjectTaskSetWorker.class);
    private final ObjectTaskSetProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectTaskSetDelivery delivery;
    private final RabbitTemplate broker;
    private final AtomicBoolean busy = new AtomicBoolean();

    ObjectTaskSetWorker(
            ObjectTaskSetProcessor processor,
            ObjectTaskSetDelivery delivery,
            @Qualifier("taskSetRabbitTemplate") RabbitTemplate broker,
            @Qualifier("taskSetExecutor") ThreadPoolTaskExecutor executor) {
        this.processor = processor;
        this.delivery = delivery;
        this.broker = broker;
        this.executor = executor;
    }

    @Bean
    Queue taskSetGenerationQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean(name = "taskSetRabbitTemplate", defaultCandidate = false)
    static RabbitTemplate template(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @RabbitListener(queues = QUEUE, concurrency = "1", autoStartup = "${app.tasks.generation.consumer-enabled:true}")
    void receive(String message) {
        try {
            processor.process(UUID.fromString(message));
        } catch (RuntimeException failure) {
            log.warn(
                    "Task-set delivery will be retried from its stored job ({})",
                    failure.getClass().getSimpleName());
        }
    }

    public boolean dispatchNext() {
        var next = delivery.next();
        if (next.isEmpty()) return false;
        var id = next.get();
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        broker.convertAndSend(
                "",
                QUEUE,
                id.toString(),
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlation);
        try {
            if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null)
                throw new IllegalStateException("Task-set delivery was not confirmed");
            delivery.sent(id);
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task-set delivery interrupted", failure);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Task-set delivery was not confirmed", failure);
        }
    }

    @Bean(name = "taskSetExecutor", defaultCandidate = false)
    static ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("task-set-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Scheduled(
            fixedDelayString = "${app.tasks.generation.poll-delay-ms:500}",
            initialDelayString = "${app.tasks.generation.initial-delay-ms:1000}")
    void poll() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    dispatchNext();
                } catch (RuntimeException failure) {
                    log.warn(
                            "Task-set worker will retry on the next poll ({})",
                            failure.getClass().getSimpleName());
                } finally {
                    busy.set(false);
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException failure) {
            busy.set(false);
        }
    }
}
