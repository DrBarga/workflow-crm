package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.audit.api.ContactAudit;
import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ContactPatch(long version, Set<String> supplied, ContactDetails details, UUID typeId, String comment) {
    public ContactPatch {
        supplied = Set.copyOf(supplied);
    }

    Map<String, ContactAudit.Change> apply(Contact contact) {
        var before = contact.details();
        var after = new ContactDetails(
                supplied.contains("name") ? details.name() : before.name(),
                supplied.contains("role") ? details.role() : before.role(),
                supplied.contains("phone") ? details.phone() : before.phone(),
                supplied.contains("email") ? details.email() : before.email());
        UUID nextType = supplied.contains("contactTypeId") ? typeId : contact.getContactTypeId();
        String nextComment = supplied.contains("comment") ? comment : contact.getComment();
        if (after.phone() == null && after.email() == null)
            throw new UnprocessableInputException("contact", "Provide phone or email for the contact");
        var changed = new LinkedHashMap<String, ContactAudit.Change>();
        difference(changed, "name", before.name(), after.name());
        difference(changed, "role", before.role(), after.role());
        difference(changed, "phone", before.phone(), after.phone());
        difference(changed, "email", before.email(), after.email());
        difference(changed, "contactTypeId", contact.getContactTypeId(), nextType);
        difference(changed, "comment", contact.getComment(), nextComment);
        contact.replace(after, nextType, nextComment);
        return Map.copyOf(changed);
    }

    private static void difference(Map<String, ContactAudit.Change> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after))
            fields.put(
                    name,
                    new ContactAudit.Change(
                            before == null ? null : before.toString(), after == null ? null : after.toString()));
    }
}
