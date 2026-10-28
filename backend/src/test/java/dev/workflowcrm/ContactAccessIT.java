package dev.workflowcrm;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactAccessIT extends ContactApiTestSupport {
    @Test
    void isolatesContactsAndAllowsRegionalEditsUsingCurrentDatabaseRole() throws Exception {
        var id = contactId(createdContact());
        getContact(colleague, id).andExpect(status().isNotFound());
        getContact(outsider, id).andExpect(status().isNotFound());
        var object = objectId(manager);
        link(object, id);
        getContact(lead, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(false));
        patchContact(lead, id, Map.of("version", 0, "comment", "Updated")).andExpect(status().isOk());
        patchContact(colleague, id, Map.of("version", 0, "comment", "No")).andExpect(status().isNotFound());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        getContact(manager, id).andExpect(status().isForbidden());
    }

    @Test
    void readOnlyRolesCannotMutateAndContractManagerSeesOnlyContractStages() throws Exception {
        var id = contactId(createdContact());
        var object = objectId(manager);
        link(object, id);
        for (var role : java.util.List.of("CEO", "CONTRACT_MANAGER")) {
            var actor = UUID.randomUUID();
            user(actor, role, null);
            postContact(actor, contactBody()).andExpect(status().isForbidden());
            patchContact(actor, id, Map.of("version", 0, "name", "No")).andExpect(status().isForbidden());
            deleteContact(actor, id, 0, null).andExpect(status().isForbidden());
            getContact(actor, id).andExpect(role.equals("CONTRACT_MANAGER") ? status().isNotFound() : status().isOk());
            if (role.equals("CONTRACT_MANAGER")) {
                jdbc.update(
                        "update objects set status_id=(select id from object_statuses where code='CONTRACT_APPROVAL') where id=?",
                        object);
                getContact(actor, id).andExpect(status().isOk());
            }
        }
    }

    @Test
    void cardAndDuplicateWarningNeverExposeObjectsOutsideScope() throws Exception {
        var id = contactId(createdContact());
        var own = objectId(manager);
        var hidden = objectId(colleague);
        link(own, id);
        link(hidden, id);
        getContact(manager, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()").value(1))
                .andExpect(jsonPath("$.objects[0].id").value(own.toString()));
        postContact(colleague, contactBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicateContacts[0].id").value(id.toString()))
                .andExpect(jsonPath("$.duplicateContacts[0].phone").doesNotExist());
        postContact(outsider, contactBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicateContacts").isEmpty());
    }
}
