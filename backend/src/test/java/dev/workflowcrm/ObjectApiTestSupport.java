package dev.workflowcrm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.ReverseGeocoder;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectApiTestSupport extends ObjectTestInfrastructure {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JwtEncoder tokens;

    @MockitoBean
    ReverseGeocoder geocoder;

    final UUID region = UUID.fromString("00000000-0000-0000-0400-000000000001"),
            otherRegion = UUID.fromString("00000000-0000-0000-0400-000000000002"),
            manager = UUID.fromString("00000000-0000-0000-0400-000000000003"),
            lead = UUID.fromString("00000000-0000-0000-0400-000000000004"),
            colleague = UUID.fromString("00000000-0000-0000-0400-000000000005"),
            outsider = UUID.fromString("00000000-0000-0000-0400-000000000006");
    Cookie csrf;
    String csrfToken;

    @BeforeEach
    void setupObjects() throws Exception {
        ObjectTestCleanup.reset(jdbc);
        jdbc.update(
                "insert into regions(id,code,name) values (?, 'UA-30','Київ'), (?, 'UA-12','Дніпропетровська область')",
                region,
                otherRegion);
        user(manager, "MANAGER", region);
        user(lead, "REGIONAL_MANAGER", region);
        user(colleague, "MANAGER", region);
        user(outsider, "MANAGER", otherRegion);
        reset(geocoder);
        when(geocoder.resolve(any(), any()))
                .thenReturn(new ReverseGeocoder.Location("Хрещатик, Київ", "UA-30", "Київ"));
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrf = response.getCookie("XSRF-TOKEN");
        csrfToken =
                json.readTree(response.getContentAsByteArray()).path("token").asText();
    }

    void user(UUID id, String role, UUID area) {
        jdbc.update(
                "insert into users(id,email,password_hash,full_name,role_id,status) "
                        + "select ?,?, 'test-hash','Object Test User',id,'ACTIVE' from roles where code=?",
                id,
                id + "@example.com",
                role);
        UserRegionTestData.assign(jdbc, id, area);
    }

    Cookie cookie(UUID actor) {
        return ObjectTestSession.cookie(tokens, actor);
    }

    Map<String, Object> body() {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", "Тестовий об’єкт");
        result.put("objectType", "BUILT_IN_PREMISES");
        result.put("address", "Адреса вручну");
        result.put("regionId", region.toString());
        result.put("entrance", Map.of("longitude", 30.5234, "latitude", 50.4501));
        result.put("objectArea", 120.5);
        return result;
    }

    ResultActions create(UUID actor, String key, Map<String, Object> body) throws Exception {
        var request = post("/api/objects")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body));
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request);
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
