package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.integrations.api.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FrontendGeoApiIT extends ObjectApiTestSupport {
    @MockitoBean
    ForwardGeocoder forward;

    @Test
    void searchValidatesInputAndReturnsBoundedSuggestions() throws Exception {
        var option = new ForwardGeocoder.Location("Київ", new BigDecimal("50.4501"), new BigDecimal("30.5234"));
        when(forward.search("Київ")).thenReturn(Collections.nCopies(6, option));
        var result = json.readTree(mvc.perform(get("/api/geo/search")
                        .param("q", "Київ")
                        .param("limit", "2")
                        .cookie(cookie(manager)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(result).hasSize(2);
        assertThat(result.get(0).path("latitude").decimalValue()).isEqualByComparingTo("50.4501");
        mvc.perform(get("/api/geo/search").param("q", "Ки").cookie(cookie(manager)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/geo/search")
                        .param("q", "Київ")
                        .param("limit", "7")
                        .cookie(cookie(manager)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/geo/search").param("q", "Київ")).andExpect(status().isUnauthorized());
        when(forward.search("Київ")).thenThrow(new GeocodingUnavailableException());
        mvc.perform(get("/api/geo/search").param("q", "Київ").cookie(cookie(manager)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void reverseWorksBeforeObjectSaveAndRequiresManualRegionWhenUnmapped() throws Exception {
        var data = Map.of("longitude", 30.5234, "latitude", 50.4501);
        var first = json.readTree(reverse(manager, data)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(first.path("address").asText()).isEqualTo("Хрещатик, Київ");
        assertThat(first.path("regionId").isNull()).isTrue();
        assertThat(first.path("requiresRegionSelection").asBoolean()).isTrue();
        assertThat(count("objects")).isZero();
        ObjectRegionBoundaryFixture.load(jdbc, region);
        var mapped = json.readTree(reverse(manager, data)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(mapped.path("regionId").asText()).isEqualTo(region.toString());
        assertThat(mapped.path("requiresRegionSelection").asBoolean()).isFalse();
        var foreign = json.readTree(reverse(outsider, data)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(foreign.path("regionId").isNull()).isTrue();
        assertThat(count("objects")).isZero();
    }

    @Test
    void reverseRejectsInvalidForeignAndUnexpectedInputsWithoutProviderCalls() throws Exception {
        clearInvocations(geocoder);
        reverse(manager, Map.of("longitude", 0, "latitude", 0)).andExpect(status().isUnprocessableEntity());
        reverse(manager, Map.of("longitude", 30.523412345, "latitude", 50.4501))
                .andExpect(status().isUnprocessableEntity());
        reverse(manager, Map.of("longitude", "30.52", "latitude", 50.45)).andExpect(status().isUnprocessableEntity());
        reverse(manager, Map.of("longitude", 30.52)).andExpect(status().isUnprocessableEntity());
        reverse(manager, Map.of("longitude", 30.52, "latitude", 50.45, "objectId", "unexpected"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(geocoder);
        mvc.perform(post("/api/geo/reverse")
                        .cookie(cookie(manager))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions reverse(UUID actor, Map<String, ?> body)
            throws Exception {
        return mvc.perform(post("/api/geo/reverse")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }
}
