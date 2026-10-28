package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ObjectCreationHttpSecurityIT extends ObjectApiTestSupport {
    @Test
    void requiresAuthenticatedSessionAndCsrfAndAllowsIdempotencyCorsHeader() throws Exception {
        mvc.perform(post("/api/objects")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(body())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/objects")
                        .cookie(cookie(manager))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/objects")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsString("idempotency-key")));
    }
}
