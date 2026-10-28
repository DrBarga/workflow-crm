package dev.workflowcrm.objects.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectTaskSetDelivery {
    private final ObjectTaskSetQueue queue;

    ObjectTaskSetDelivery(ObjectTaskSetQueue queue) {
        this.queue = queue;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> next() {
        return queue.delivery();
    }

    @Transactional
    public void sent(UUID id) {
        queue.sent(id);
    }
}
