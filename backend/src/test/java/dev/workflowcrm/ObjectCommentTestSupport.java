package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectCommentTestSupport extends ObjectListTestSupport {
    ResultActions postComment(UUID actor, UUID object, Object body) throws Exception {
        return mvc.perform(post("/api/objects/" + object + "/comments")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }

    JsonNode comment(UUID actor, UUID object, String text) throws Exception {
        return json.readTree(postComment(actor, object, Map.of("text", text))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    UUID id(JsonNode comment) {
        return UUID.fromString(comment.path("id").asText());
    }

    ResultActions patchComment(UUID actor, UUID object, UUID comment, Object body) throws Exception {
        return mvc.perform(patch("/api/objects/" + object + "/comments/" + comment)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }

    ResultActions removeComment(UUID actor, UUID object, UUID comment) throws Exception {
        return mvc.perform(delete("/api/objects/" + object + "/comments/" + comment)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken));
    }

    ResultActions commentRequest(UUID actor, UUID object, Map<String, String> parameters) throws Exception {
        var request = get("/api/objects/" + object + "/comments").cookie(cookie(actor));
        parameters.forEach(request::param);
        return mvc.perform(request);
    }

    JsonNode comments(UUID actor, UUID object, Map<String, String> parameters) throws Exception {
        return json.readTree(commentRequest(actor, object, parameters)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    UUID seed(UUID object, UUID actor, String text) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into object_comments(id,object_id,author_id,body) values(?,?,?,?)", id, object, actor, text);
        return id;
    }
}
