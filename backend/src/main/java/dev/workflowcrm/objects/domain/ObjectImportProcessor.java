package dev.workflowcrm.objects.domain;

import dev.workflowcrm.audit.api.ObjectDuplicateAudit;
import dev.workflowcrm.contacts.api.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ObjectImportProcessor {
    private static final Logger log = LoggerFactory.getLogger(ObjectImportProcessor.class);
    private final ObjectImportAccess access;
    private final ObjectImportStore imports;
    private final ObjectImportDecoder decoder;
    private final ObjectCreationPreparation preparation;
    private final ObjectCreationStore objects;
    private final ObjectDuplicateSearch duplicates;
    private final ObjectDuplicateAudit duplicateAudit;
    private final ContactRegistration contacts;
    private final TransactionTemplate transactions;

    public ObjectImportProcessor(
            ObjectImportAccess access,
            ObjectImportStore imports,
            ObjectImportDecoder decoder,
            ObjectCreationPreparation preparation,
            ObjectCreationStore objects,
            ObjectDuplicateSearch duplicates,
            ObjectDuplicateAudit duplicateAudit,
            ContactRegistration contacts,
            PlatformTransactionManager manager) {
        this.access = access;
        this.imports = imports;
        this.decoder = decoder;
        this.preparation = preparation;
        this.objects = objects;
        this.duplicates = duplicates;
        this.duplicateAudit = duplicateAudit;
        this.contacts = contacts;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean processNext() {
        var claimed = new AtomicReference<ObjectImport.Job>();
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                var next = imports.claim();
                if (next.isEmpty()) return false;
                var job = next.get();
                claimed.set(job);
                access.require(job.actor());
                var prepared = new ArrayList<ObjectImport.Prepared>();
                for (var row : imports.inputs(job.id())) {
                    interrupted();
                    try {
                        var command = decoder.decode(row);
                        prepared.add(new ObjectImport.Prepared(
                                row,
                                preparation.prepareImport(job.actor(), command.object()),
                                command.dates(),
                                List.of()));
                    } catch (UnprocessableInputException invalid) {
                        prepared.add(new ObjectImport.Prepared(row, null, null, invalid.violations()));
                    }
                }
                access.require(job.actor());
                objects.lockCreation();
                for (var row : prepared) {
                    interrupted();
                    if (!row.errors().isEmpty()) {
                        imports.row(
                                job.id(),
                                new ObjectImport.Row(row.input().row(), "REJECTED", null, false, null, row.errors()));
                        continue;
                    }
                    var matches = duplicates.find(row.registration());
                    var object = objects.save(row.registration(), row.dates());
                    var contact = row.registration().contact();
                    UUID contactId = contact == null
                            ? null
                            : contacts.attachImported(
                                    object.getId(),
                                    job.actor(),
                                    new ContactDetails(
                                            contact.getName(),
                                            contact.getRole(),
                                            contact.getPhone(),
                                            contact.getEmail()));
                    String key = "import:" + job.id() + ":" + row.input().row();
                    objects.recordCreation(job.actor(), key, job.hash(), object, contactId);
                    if (matches.total() > 0) {
                        duplicateAudit.record(
                                job.actor(), key, job.hash(), object.getId(), matches.total(), matches.ids());
                        objects.recordDuplicateConfirmation(job.actor(), object, matches.total());
                    }
                    imports.row(
                            job.id(),
                            new ObjectImport.Row(
                                    row.input().row(),
                                    "CREATED",
                                    object.getId(),
                                    matches.total() > 0,
                                    matches.total() > 0 ? "можливий дубль" : null,
                                    List.of()));
                }
                access.require(job.actor());
                imports.complete(job.id());
                return true;
            }));
        } catch (RuntimeException failure) {
            var job = claimed.get();
            if (job == null) throw failure;
            String reason = failure instanceof AccessDeniedException ? "PERMISSIONS_CHANGED" : "IMPORT_FAILED";
            transactions.executeWithoutResult(
                    tx -> imports.failed(job.id(), reason, !(failure instanceof AccessDeniedException)));
            log.warn("Import {} rolled back ({})", job.id(), reason);
            return true;
        }
    }

    private static void interrupted() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Import interrupted");
    }
}
