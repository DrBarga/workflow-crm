package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectTaskSetService {
    private final ObjectStatusStore objects;
    private final ObjectStatusAccess access;
    private final ObjectStatusService statuses;
    private final ObjectStatusApprovalService approvals;
    private final ObjectStatusFingerprint commands;
    private final ObjectTaskSetQueue queue;

    ObjectTaskSetService(
            ObjectStatusStore objects,
            ObjectStatusAccess access,
            ObjectStatusService statuses,
            ObjectStatusFingerprint commands,
            ObjectTaskSetQueue queue,
            ObjectStatusApprovalService approvals) {
        this.objects = objects;
        this.access = access;
        this.statuses = statuses;
        this.commands = commands;
        this.queue = queue;
        this.approvals = approvals;
    }

    @Transactional
    public ObjectStatusResult preview(UUID actor, UUID object, ObjectTaskSetCommand command) {
        return command.approvalRequestId() == null
                ? statuses.preview(actor, object, command.transition())
                : approvals.preview(actor, object, command.approvalRequestId(), command.assignees());
    }

    @Transactional
    public ObjectTaskSetJob submit(UUID actor, UUID object, String key, ObjectTaskSetCommand command) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            throw new UnprocessableInputException(
                    "Idempotency-Key", "Provide an idempotency key of 1 to 128 characters");
        if (command.confirmationToken() == null)
            throw new UnprocessableInputException("confirmationToken", "Confirm the task preview before submitting");
        queue.lockKey(actor, key);
        access.require(actor, objects.lock(object));
        String encoded = command.transition() != null
                ? commands.encode(command.transition())
                : command.assignees().isEmpty()
                        ? "approval:" + command.approvalRequestId() + ":" + command.confirmationToken()
                        : commands.encode(command);
        String fingerprint = digest(object + ":" + encoded);
        var previous = queue.submission(actor, key);
        if (previous.isPresent()) {
            if (!previous.get().fingerprint().equals(fingerprint))
                throw conflict("Idempotency key was used for another request");
            return previous.get().job();
        }
        var confirmed = queue.confirmation(actor, object, command.confirmationToken());
        if (confirmed.isPresent()) {
            if (!confirmed.get().fingerprint().equals(fingerprint)) throw ObjectStatusExecutor.conflict();
            queue.remember(actor, key, fingerprint, confirmed.get().job().id());
            return confirmed.get().job();
        }
        if (queue.pending(object)) throw conflict("Task-set creation is already queued for this object");
        var preview = preview(actor, object, command);
        if (preview.confirmationToken() == null)
            throw new UnprocessableInputException(
                    "assignees", "Only the user completing the transition can confirm a fully assigned preview");
        if (!MessageDigest.isEqual(
                preview.confirmationToken().getBytes(StandardCharsets.UTF_8),
                command.confirmationToken().getBytes(StandardCharsets.UTF_8))) throw ObjectStatusExecutor.conflict();
        var job = queue.enqueue(actor, object, command.confirmationToken(), command);
        queue.remember(actor, key, fingerprint, job.id());
        return job;
    }

    @Transactional
    public ObjectTaskSetJob get(UUID actor, UUID object, UUID job) {
        access.require(actor, objects.lock(object));
        return queue.get(job, actor, object);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
