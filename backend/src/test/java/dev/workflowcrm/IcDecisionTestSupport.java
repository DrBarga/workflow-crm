package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class IcDecisionTestSupport extends ObjectApiTestSupport {
    UUID object, director = UUID.fromString("00000000-0000-0000-0671-000000000001");

    @BeforeEach
    void prepareIc() throws Exception {
        user(director, "DEVELOPMENT_DIRECTOR", null);
        var response = create(manager, key(), body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        object = UUID.fromString(
                json.readTree(response.getContentAsByteArray()).path("id").asText());
        jdbc.update(
                "update objects set status_id = (select id from object_statuses where code='IC_PREPARATION') where id=?",
                object);
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from object_history"));
    }

    @AfterEach
    void cleanTasks() {
        jdbc.update("delete from task_history");
        jdbc.update("delete from tasks");
        jdbc.update("delete from task_sets");
        jdbc.update("delete from task_template_items");
        jdbc.update("delete from task_templates");
    }

    Map<String, Object> decision(String value) {
        var body = new LinkedHashMap<String, Object>();
        body.put("decision", value);
        body.put("date", "2026-10-05");
        body.put("version", 0);
        if ("APPROVED_WITH_CONDITIONS".equals(value)) body.put("conditions", "  Погодити орендну ставку  ");
        if ("REFUSED".equals(value)) body.put("refusalComment", "Не відповідає вимогам");
        return body;
    }

    ResultActions decide(UUID actor, Map<String, Object> body) throws Exception {
        return mvc.perform(patch("/api/objects/" + object + "/ic-decision")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    JsonNode response(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    String state() {
        return jdbc.queryForObject(
                "select s.code from objects o join object_statuses s on s.id=o.status_id where o.id=?",
                String.class,
                object);
    }

    List<String> taskSnapshot() {
        return jdbc.queryForList(
                "select to_jsonb(t)::text from tasks t where source_status_id="
                        + "(select id from object_statuses where code='IC_PREPARATION') order by id",
                String.class);
    }

    int decisionHistoryCount() {
        return jdbc.queryForObject(
                "select count(*) from object_history where field_name in ('IC_DECISION','STATUS')", Integer.class);
    }

    UUID template() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_templates(id,trigger_status_id) select ?,id from object_statuses where code='COMMERCIAL_TERMS_APPROVAL'",
                id);
        jdbc.update(
                "insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order) values (?,'Перевірити комерційні умови',?,3,0)",
                id,
                manager);
        return id;
    }

    UUID task(String state) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into tasks(id,object_id,source_status_id,name,assignee_id,deadline,status,author_id)
                select ?,?,s.id,'Підготувати ІК',?,'2026-10-04',?,? from object_statuses s where code='IC_PREPARATION'
                """,
                id,
                object,
                manager,
                state,
                manager);
        return id;
    }
}
