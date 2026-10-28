package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectExportProcessor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.objects.export.worker-enabled", havingValue = "true", matchIfMissing = true)
class ObjectExportWorker {
    private static final Logger log = LoggerFactory.getLogger(ObjectExportWorker.class);
    private final ObjectExportProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean busy = new AtomicBoolean();

    ObjectExportWorker(
            ObjectExportProcessor processor, @Qualifier("objectExportExecutor") ThreadPoolTaskExecutor executor) {
        this.processor = processor;
        this.executor = executor;
    }

    @Bean(name = "objectExportExecutor", defaultCandidate = false)
    static ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("object-export-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Scheduled(fixedDelayString = "${app.objects.export.poll-delay-ms:1000}")
    void poll() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    processor.cleanupNext();
                    processor.processNext();
                } catch (RuntimeException e) {
                    log.warn(
                            "Export worker will retry on the next poll ({})",
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
