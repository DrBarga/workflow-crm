package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectDeletionProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.objects.deletion.worker-enabled", havingValue = "true", matchIfMissing = true)
class ObjectDeletionWorker {
    private final ObjectDeletionProcessor processor;

    ObjectDeletionWorker(ObjectDeletionProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.objects.deletion.poll-delay-ms:5000}")
    void process() {
        for (int i = 0; i < 10 && processor.processNext(); i++) {}
    }
}
