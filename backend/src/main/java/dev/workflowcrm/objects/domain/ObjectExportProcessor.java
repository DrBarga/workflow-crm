package dev.workflowcrm.objects.domain;

import dev.workflowcrm.integrations.api.BinaryStorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ObjectExportProcessor {
    private static final Logger log = LoggerFactory.getLogger(ObjectExportProcessor.class);
    private final ObjectExportAccess access;
    private final ObjectExportStore store;
    private final ObjectExportRenderer renderer;
    private final ObjectExportFiles files;
    private final ObjectExportSettings settings;
    private final Clock clock;
    private final TransactionTemplate transactions;

    ObjectExportProcessor(
            ObjectExportAccess access,
            ObjectExportStore store,
            ObjectExportRenderer renderer,
            ObjectExportFiles files,
            ObjectExportSettings settings,
            Clock clock,
            PlatformTransactionManager manager) {
        this.access = access;
        this.store = store;
        this.renderer = renderer;
        this.files = files;
        this.settings = settings;
        this.clock = clock;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public boolean processNext() {
        var claimed = new AtomicReference<ObjectExport.Job>();
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                var next = store.claim(clock.instant());
                if (next.isEmpty()) return false;
                var job = next.get();
                claimed.set(job);
                access.unchanged(access.require(job.actor()), job.scope());
                files.purge(job);
                Path temp = null;
                try {
                    temp = Files.createTempFile(
                            "workflow-export-", "." + job.format().extension());
                    long rows = renderer.render(job, temp, settings.maxFileBytes());
                    files.upload(job, temp);
                    store.ready(job.id(), rows, Files.size(temp));
                    return true;
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                } finally {
                    if (temp != null)
                        try {
                            Files.deleteIfExists(temp);
                        } catch (IOException failure) {
                            log.warn("Cannot remove temporary export file {}", job.id());
                        }
                }
            }));
        } catch (RuntimeException failure) {
            if (claimed.get() == null) throw failure;
            var job = claimed.get();
            String error = failure instanceof AccessDeniedException
                    ? "PERMISSIONS_CHANGED"
                    : failure instanceof BinaryStorageException
                            ? "STORAGE_UNAVAILABLE"
                            : failure instanceof ObjectExportLimitException ? "EXPORT_LIMIT" : "EXPORT_FAILED";
            boolean retry = !error.equals("PERMISSIONS_CHANGED") && !error.equals("EXPORT_LIMIT");
            transactions.executeWithoutResult(tx -> store.failed(job.id(), error, retry));
            log.warn("Export {} failed ({})", job.id(), error);
            return true;
        }
    }

    public boolean cleanupNext() {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            var job = store.expired(clock.instant());
            if (job.isEmpty()) return false;
            files.purge(job.get());
            store.expire(job.get().id());
            return true;
        }));
    }
}
