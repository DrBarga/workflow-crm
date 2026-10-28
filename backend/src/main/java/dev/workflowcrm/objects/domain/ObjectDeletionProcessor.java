package dev.workflowcrm.objects.domain;

import dev.workflowcrm.files.api.ObjectFileStorage;
import dev.workflowcrm.integrations.api.BinaryStorageException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ObjectDeletionProcessor {
    private static final Logger log = LoggerFactory.getLogger(ObjectDeletionProcessor.class);
    private final ObjectDeletionStore store;
    private final ObjectFileStorage files;
    private final TransactionTemplate transactions;

    ObjectDeletionProcessor(ObjectDeletionStore store, ObjectFileStorage files, PlatformTransactionManager manager) {
        this.store = store;
        this.files = files;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean processNext() {
        var claimed = new AtomicReference<UUID>();
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                var candidate = store.claimNext();
                if (candidate.isEmpty()) return false;
                var operation = candidate.get();
                claimed.set(operation.id());
                store.lockObject(operation.objectId());
                for (var file : store.files(operation.id())) files.purge(file);
                store.complete(operation);
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed.get() == null) throw failure;
            String error = failure instanceof BinaryStorageException ? "STORAGE_UNAVAILABLE" : "DATABASE_FAILURE";
            transactions.executeWithoutResult(status -> store.failed(claimed.get(), error));
            log.warn("Object deletion {} will be retried ({})", claimed.get(), error);
            return true;
        }
    }
}
