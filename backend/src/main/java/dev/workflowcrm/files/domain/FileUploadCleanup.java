package dev.workflowcrm.files.domain;

import dev.workflowcrm.integrations.api.BinaryStorage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileUploadCleanup {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCleanup.class);
    private final FileUploadReservations reservations;
    private final BinaryStorage storage;
    private final TransactionTemplate transactions;

    FileUploadCleanup(FileUploadReservations reservations, BinaryStorage storage, PlatformTransactionManager manager) {
        this.reservations = reservations;
        this.storage = storage;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean processNext() {
        var claimed = new AtomicReference<UUID>();
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                var expired = reservations.expired();
                if (expired.isEmpty()) return false;
                var row = expired.get();
                claimed.set(row.id());
                storage.deleteAllVersions(row.bucket(), row.key());
                reservations.complete(row.id());
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed.get() == null) throw failure;
            transactions.executeWithoutResult(status -> reservations.retry(claimed.get()));
            log.warn("Failed upload {} will be cleaned up again", claimed.get());
            return true;
        }
    }
}
