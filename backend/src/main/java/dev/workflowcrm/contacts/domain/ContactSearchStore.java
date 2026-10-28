package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.shared.read.ReadPage;
import java.util.UUID;

public interface ContactSearchStore {
    ReadPage.Result<ContactSummary> search(ContactScope scope, UUID object, String query, ReadPage page);
}
