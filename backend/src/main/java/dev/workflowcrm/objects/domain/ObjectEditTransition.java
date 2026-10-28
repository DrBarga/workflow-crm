package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ObjectEditTransition {
    private final ObjectStatusStore store;
    private final ObjectStatusAccess access;
    private final ObjectStatusExecutor executor;
    private final ObjectEditFingerprint fingerprint;

    ObjectEditTransition(
            ObjectStatusStore store,
            ObjectStatusAccess access,
            ObjectStatusExecutor executor,
            ObjectEditFingerprint fingerprint) {
        this.store = store;
        this.access = access;
        this.executor = executor;
        this.fingerprint = fingerprint;
    }

    ObjectStatusResult apply(
            UUID actor,
            RealEstateObject object,
            ObjectPatchCommand patch,
            ObjectEditPreparation.Prepared prepared,
            Runnable edit) {
        var date = prepared.milestones();
        boolean signing = patch.fields().contains("contractSignedDate") && date.contractSignedDate() != null;
        boolean opening = patch.fields().contains("openingActualDate") && date.openingActualDate() != null;
        var signingRules = signing
                ? store.transitions(object.getStatus().getId(), null, "CONTRACT_SIGNED_DATE")
                : List.<ObjectStatusTransition>of();
        var openingRules = opening
                ? store.transitions(object.getStatus().getId(), null, "OPENING_DATE")
                : List.<ObjectStatusTransition>of();
        signing = signing
                && (!date.contractSignedDate().equals(object.getContractSignedDate()) || !signingRules.isEmpty());
        opening = opening && (!date.openingActualDate().equals(object.getOpeningDate()) || !openingRules.isEmpty());
        if (signing && opening)
            throw new UnprocessableInputException("openingActualDate", "Change one lifecycle date per request");
        var candidates = new ArrayList<ObjectStatusTransition>();
        if (signing) candidates.addAll(signingRules);
        if (opening) candidates.addAll(openingRules);
        if (candidates.isEmpty()) {
            if (signing && object.getContractSignedDate() == null)
                throw new UnprocessableInputException(
                        "contractSignedDate", "Signing transition is not configured from this status");
            if (opening && object.getOpeningDate() == null)
                throw new UnprocessableInputException(
                        "openingActualDate", "Opening transition is not configured from this status");
            if (patch.confirmationToken() != null) throw new ObjectEditConflictException(object.getVersion());
            return null;
        }
        var rule = access.select(access.require(actor, object), object, candidates);
        if (rule.isConfirmationRequired())
            throw new UnprocessableInputException("body", "Use the status action for transitions requiring approval");
        var signed = rule.getRequiredFields().contains(TransitionRequiredField.CONTRACT_SIGNED_DATE)
                ? date.contractSignedDate()
                : null;
        var opened = rule.getRequiredFields().contains(TransitionRequiredField.OPENING_DATE)
                ? date.openingActualDate()
                : null;
        var type = rule.getRequiredFields().contains(TransitionRequiredField.CONTRACT_TYPE)
                ? patch.values().contractTypeId()
                : null;
        var command = new ObjectStatusCommand(
                null,
                patch.version(),
                null,
                signed,
                type,
                opened,
                date.openingPlannedDate(),
                false,
                null,
                signing ? ObjectSigningDetails.prepared(date, prepared.commercial()) : null,
                Map.of());
        var preview = executor.run(actor, object, rule, command, false, null, null, o -> edit.run(), true);
        if (preview.confirmationToken() == null) {
            if (patch.confirmationToken() != null) throw new ObjectEditConflictException(object.getVersion());
            return executor.run(actor, object, rule, command, false, null, null, o -> edit.run(), false);
        }
        String token = fingerprint.token(actor, patch, preview.confirmationToken());
        if (patch.confirmationToken() == null)
            return new ObjectStatusResult(
                    false,
                    object.getId(),
                    object.getStatus().getCode(),
                    object.getVersion(),
                    null,
                    token,
                    preview.tasks());
        if (!MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                patch.confirmationToken().getBytes(StandardCharsets.UTF_8)))
            throw new ObjectEditConflictException(object.getVersion());
        command = new ObjectStatusCommand(
                null,
                patch.version(),
                null,
                signed,
                type,
                opened,
                date.openingPlannedDate(),
                false,
                preview.confirmationToken(),
                signing ? ObjectSigningDetails.prepared(date, prepared.commercial()) : null,
                Map.of());
        return executor.run(actor, object, rule, command, false, null, null, o -> edit.run(), false);
    }
}
