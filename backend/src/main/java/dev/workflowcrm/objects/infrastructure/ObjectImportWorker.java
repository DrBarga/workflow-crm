package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectImportProcessor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.objects.import.worker-enabled", havingValue = "true", matchIfMissing = true)
class ObjectImportWorker {
    private static final Logger log = LoggerFactory.getLogger(ObjectImportWorker.class);
    private final ObjectImportProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean busy = new AtomicBoolean();

    ObjectImportWorker(
            ObjectImportProcessor processor, @Qualifier("objectImportExecutor") ThreadPoolTaskExecutor executor) {
        this.processor = processor;
        this.executor = executor;
    }

    @Bean(name = "objectImportExecutor", defaultCandidate = false)
    static ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("object-import-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Scheduled(fixedDelayString = "${app.objects.import.poll-delay-ms:1000}")
    void poll() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    processor.processNext();
                } catch (RuntimeException e) {
                    log.warn(
                            "Import worker will retry on the next poll ({})",
                            e.getClass().getSimpleName());
                } finally {
                    busy.set(false);
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException e) {
            busy.set(false);
        }
    }
}
