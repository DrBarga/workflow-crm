package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.integrations.api.GeocodingUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCreationIT extends ObjectApiTestSupport {
    @Test
    void createsWithServerMetadataAllAcceptedFieldsAndHistory() throws Exception {
        var body = body();
        body.put("landArea", 200);
        body.put(
                "contact",
                Map.of(
                        "name",
                        "Власник",
                        "role",
                        "Власник",
                        "phone",
                        "+380 (67) 123-45-67",
                        "email",
                        " Owner@Example.COM "));
        var response = create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FOUND"))
                .andExpect(jsonPath("$.statusName").value("Знайдено"))
                .andExpect(jsonPath("$.authorId").value(manager.toString()))
                .andExpect(jsonPath("$.managerId").value(manager.toString()))
                .andExpect(jsonPath("$.contact.phone").value("+380671234567"))
                .andExpect(jsonPath("$.contact.email").value("owner@example.com"))
                .andReturn()
                .getResponse();
        var saved = json.readTree(response.getContentAsByteArray());
        assertThat(response.getHeader("Location"))
                .isEqualTo("/api/objects/" + saved.path("id").asText());
        assertThat(saved.path("createdAt").asText()).isNotBlank();
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select author_id::text from object_history", String.class))
                .isEqualTo(manager.toString());
        assertThat(jdbc.queryForObject("select field_name from object_history", String.class))
                .isEqualTo("CREATED");
        verifyNoInteractions(geocoder);
    }

    @Test
    void acceptsOnlyLandAreaAndNoContactUntilContactsEpic() throws Exception {
        var body = body();
        body.remove("objectArea");
        body.put("landArea", 100);
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contact").isEmpty())
                .andExpect(jsonPath("$.landArea").value(100));
    }

    @Test
    void fillsAddressAndRegionByCoordinates() throws Exception {
        ObjectRegionBoundaryFixture.load(jdbc, region);
        var body = body();
        body.remove("address");
        body.remove("regionId");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value("Хрещатик, Київ"))
                .andExpect(jsonPath("$.regionId").value(region.toString()));
        verify(geocoder).resolve(any(), any());
    }

    @Test
    void preservesManualOverridesIndependently() throws Exception {
        ObjectRegionBoundaryFixture.load(jdbc, region);
        var body = body();
        body.remove("regionId");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value("Адреса вручну"));
        body = body();
        body.remove("address");
        body.put("confirmDuplicates", true);
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionId").value(region.toString()));
    }

    @Test
    void geocoderFailureDoesNotCreateAndRetryCanRecover() throws Exception {
        var body = body();
        body.remove("address");
        String key = key();
        when(geocoder.resolve(any(), any())).thenThrow(new GeocodingUnavailableException());
        create(manager, key, body).andExpect(status().isServiceUnavailable());
        assertThat(count("objects")).isZero();
        doReturn(new dev.workflowcrm.integrations.api.ReverseGeocoder.Location("Київ", "UA-30", "Київ"))
                .when(geocoder)
                .resolve(any(), any());
        create(manager, key, body).andExpect(status().isCreated());
    }

    @Test
    void failedAuditRollsBackObjectAndRequestReservation() throws Exception {
        jdbc.execute(
                "alter table object_history add constraint test_creation_audit_failure check (field_name <> 'CREATED')");
        try {
            create(manager, key(), body()).andExpect(status().isInternalServerError());
            assertThat(count("objects")).isZero();
            assertThat(count("object_creation_requests")).isZero();
        } finally {
            jdbc.execute("alter table object_history drop constraint test_creation_audit_failure");
        }
    }
}
