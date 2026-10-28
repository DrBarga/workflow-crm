package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectListTestSupport extends ObjectApiTestSupport {
    final UUID director = UUID.fromString("00000000-0000-0000-0577-300000000001"),
            contract = UUID.fromString("00000000-0000-0000-0577-300000000002"),
            ceo = UUID.fromString("00000000-0000-0000-0577-300000000003"),
            admin = UUID.fromString("00000000-0000-0000-0577-300000000004");

    @BeforeEach
    void listUsers() {
        user(director, "DEVELOPMENT_DIRECTOR", null);
        user(contract, "CONTRACT_MANAGER", null);
        user(ceo, "CEO", null);
        user(admin, "SUPERADMIN", null);
    }

    UUID add(String name, UUID owner, UUID area, String created, String statusCode) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into objects(id,name,address,manager_id,region_id,status_id,object_type_id,author_id,
                    entrance_longitude,entrance_latitude,object_area,created_at,building_area,connected_power,
                    fixed_rent_amount,technical_specifications,commercial_terms,contact_name,contact_role,contact_phone)
                select ?,?,'Приватна адреса',?,?,s.id,t.id,?,30.5,50.4,120,?,100,25,9999,'Технічний опис','Умови',
                    'Контакт','Власник','+380991234567' from object_statuses s cross join object_types t
                where s.code=? and t.code='BUILT_IN_PREMISES'
                """,
                id,
                name,
                owner,
                area,
                owner,
                Timestamp.from(Instant.parse(created)),
                statusCode);
        return id;
    }

    UUID add(String name, UUID owner, UUID area) {
        return add(name, owner, area, "2026-01-15T12:00:00Z", "FOUND");
    }

    ResultActions request(UUID actor, Map<String, String> parameters) throws Exception {
        var r = get("/api/objects").cookie(cookie(actor));
        parameters.forEach(r::param);
        return mvc.perform(r);
    }

    JsonNode page(UUID actor, Map<String, String> parameters) throws Exception {
        return json.readTree(request(actor, parameters)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    List<String> names(JsonNode page) {
        var result = new ArrayList<String>();
        page.path("content").forEach(r -> result.add(r.path("name").asText()));
        return result;
    }

    Set<String> fields(JsonNode row) {
        var result = new HashSet<String>();
        row.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
