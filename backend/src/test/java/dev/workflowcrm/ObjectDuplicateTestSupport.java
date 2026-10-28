package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectDuplicateTestSupport extends ObjectApiTestSupport {
    @Override
    Map<String, Object> body() {
        var body = super.body();
        body.put("name", "Новий торговельний об’єкт");
        body.put("address", "Київ, Нова вулиця, 10");
        return body;
    }

    UUID existing(double northMetres, String name, String address, UUID owner, UUID area, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into objects(id,name,address,region_id,manager_id,status_id,entrance_longitude,entrance_latitude,object_area,author_id) "
                        + "select ?,?,?,?,?,id,30.5234,?,120,? from object_statuses where code=?",
                id,
                name,
                address,
                area,
                owner,
                50.4501 + northMetres / 111195.0802,
                owner,
                status);
        return id;
    }

    UUID nearby(double northMetres) {
        return existing(northMetres, "Наявний об’єкт " + key(), "Інша адреса " + key(), manager, region, "FOUND");
    }

    ResultActions cancel(UUID actor, String key, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/objects/duplicate-cancellations")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }
}
