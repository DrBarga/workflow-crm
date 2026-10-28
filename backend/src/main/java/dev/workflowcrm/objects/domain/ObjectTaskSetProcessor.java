package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectTaskSetProcessor {
    private static final Logger log = LoggerFactory.getLogger(ObjectTaskSetProcessor.class);
    private final ObjectTaskSetQueue queue;
    private final ObjectStatusService statuses;
    private final ObjectStatusApprovalService approvals;
    private final TransactionTemplate transactions;

    ObjectTaskSetProcessor(
            ObjectTaskSetQueue queue,
            ObjectStatusService statuses,
            ObjectStatusApprovalService approvals,
            PlatformTransactionManager manager) {
        this.queue = queue;
        this.statuses = statuses;
        this.approvals = approvals;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean processNext() {
        return process(null);
    }

    public boolean process(UUID id) {
        var claimed = new AtomicReference<ObjectTaskSetQueue.Work>();
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                var next = id == null ? queue.claim() : queue.claim(id);
                if (next.isEmpty()) return false;
                var work = next.get();
                claimed.set(work);
                var job = work.job();
                var command = work.command();
                var result = command.approvalRequestId() == null
                        ? statuses.change(job.actorId(), job.objectId(), command.transition(), false)
                        : approvals.decide(
                                job.actorId(),
                                job.objectId(),
                                command.approvalRequestId(),
                                "APPROVED",
                                command.confirmationToken(),
                                command.assignees());
                if (!result.saved() && result.approvalRequestId() == null) throw ObjectStatusExecutor.conflict();
                queue.complete(work, result);
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed.get() == null) throw failure;
            var job = claimed.get().job();
            String error = failure instanceof AccessDeniedException
                    ? "PERMISSIONS_CHANGED"
                    : failure instanceof ResponseStatusException
                            ? "PREVIEW_CHANGED"
                            : failure instanceof UnprocessableInputException
                                    ? "INVALID_TRANSITION"
                                    : "GENERATION_FAILED";
            boolean retry = failure instanceof TransientDataAccessException;
            transactions.executeWithoutResult(tx -> queue.failed(job.id(), error, retry));
            log.warn("Task-set job {} failed ({})", job.id(), error);
            return true;
        }
    }
}
