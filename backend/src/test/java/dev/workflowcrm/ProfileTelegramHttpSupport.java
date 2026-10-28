package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class ProfileTelegramHttpSupport extends ObjectTestInfrastructure {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JwtEncoder encoder;

    Cookie csrf;
    String csrfToken;

    @BeforeEach
    void csrfSession() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrf = response.getCookie("XSRF-TOKEN");
        csrfToken =
                json.readTree(response.getContentAsByteArray()).path("token").asText();
    }

    Cookie cookie(UUID id) {
        return ObjectTestSession.cookie(encoder, id);
    }

    ResultActions write(MockHttpServletRequestBuilder request, UUID actor) throws Exception {
        return mvc.perform(request.cookie(cookie(actor), csrf).header("X-XSRF-TOKEN", csrfToken));
    }

    JsonNode profile(UUID actor) throws Exception {
        return json.readTree(mvc.perform(get("/api/profile").cookie(cookie(actor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    JsonNode linkResponse(UUID actor) throws Exception {
        return json.readTree(write(post("/api/profile/telegram/link"), actor)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    String link(UUID actor) throws Exception {
        return token(linkResponse(actor).path("link").asText());
    }

    String token(String link) {
        return link.substring(link.indexOf("?start=") + 7);
    }
}
