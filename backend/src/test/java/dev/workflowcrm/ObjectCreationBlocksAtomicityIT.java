package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCreationBlocksAtomicityIT extends ObjectApiTestSupport {
    @Test
    void rollsBackBlocksContactAndIdempotencyReservationWhenJournalFails() throws Exception {
        var body = body();
        body.put("technicalSpecifications", java.util.Map.of("connectedPower", 100));
        body.put("commercialTerms", java.util.Map.of("fixedRentAmount", 1000));
        body.put("contact", Map.of("name", "Власник", "role", "Власник", "phone", "+380671234567"));
        jdbc.execute("alter table object_history add constraint test_blocks_audit check(field_name<>'CREATED')");
        String key = key();
        try {
            create(manager, key, body).andExpect(status().isInternalServerError());
            for (String table : java.util.List.of("objects", "contacts", "object_contacts", "object_creation_requests"))
                assertThat(count(table)).isZero();
        } finally {
            jdbc.execute("alter table object_history drop constraint test_blocks_audit");
        }
        create(manager, key, body).andExpect(status().isCreated());
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("contacts")).isEqualTo(1);
    }
}
