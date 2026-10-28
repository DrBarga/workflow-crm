package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectEditContactsIT extends ObjectEditTestSupport {
    UUID contact(UUID object, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into contacts(id,name,role,phone) values (?,?,'Власник','+380991234567')", id, name);
        jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", object, id);
        return id;
    }

    @Test
    void selectedContactChangeBumpsVersionAndPreservesContactRecords() throws Exception {
        UUID object = add("Об’єкт", manager, region), other = add("Інший об’єкт", manager, region);
        UUID old = contact(object, "Перший контакт"), next = contact(other, "Другий контакт");
        patch(manager, object, change(object, "contactId", next)).andExpect(status().isOk());
        assertThat(card(manager, object).path("contactId").asText()).isEqualTo(next.toString());
        assertThat(card(manager, object).path("contact").path("name").asText()).isEqualTo("Другий контакт");
        assertThat(card(manager, object).path("contact").path("phone").asText()).isEqualTo("***");
        assertThat(version(object)).isEqualTo(1);
        assertThat(count("contacts")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='contactId'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void foreignContactAndMultipleLinksCannotBeReplacedThroughCard() throws Exception {
        UUID object = add("Об’єкт", manager, region), foreign = add("Чужий об’єкт", outsider, otherRegion);
        UUID next = contact(foreign, "Чужий контакт");
        patch(manager, object, change(object, "contactId", next)).andExpect(status().isUnprocessableEntity());
        contact(object, "Перший контакт");
        contact(object, "Другий контакт");
        patch(director, object, change(object, "contactId", next)).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from object_contacts where object_id=?", Integer.class, object))
                .isEqualTo(2);
    }
}
