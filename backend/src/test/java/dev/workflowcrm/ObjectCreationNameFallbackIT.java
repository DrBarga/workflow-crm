package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.integrations.api.ReverseGeocoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCreationNameFallbackIT extends ObjectApiTestSupport {
    @Test
    void fillsOmittedNameAndAddressFromGeocoderAndReplaysWithoutCallingItAgain() throws Exception {
        var body = body();
        body.remove("name");
        body.remove("address");
        String key = key();
        String first = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Хрещатик, Київ"))
                .andExpect(jsonPath("$.googleMapsUrl")
                        .value("https://www.google.com/maps/search/?api=1&query=50.4501000%2C30.5234000"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        when(geocoder.resolve(any(), any())).thenReturn(new ReverseGeocoder.Location("Changed", "UA-30", "Київ"));
        String repeated = create(manager, key, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(repeated).isEqualTo(first);
        verify(geocoder, times(1)).resolve(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\u00a0\u202f", "\u2003"})
    void rejectsExplicitlyClearedNameAndAddressBeforeSaving(String value) throws Exception {
        var body = body();
        body.put("name", value);
        body.put("address", value);
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").value("Fill in the object name or address"));
        assertThat(count("objects")).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("object_creation_requests")).isZero();
        verifyNoInteractions(geocoder);
    }

    @Test
    void rejectsMissingNameWhenGeocodingCannotSupplyAnAddress() throws Exception {
        var body = body();
        body.remove("name");
        body.remove("address");
        when(geocoder.resolve(any(), any())).thenReturn(new ReverseGeocoder.Location("  ", "UA-30", "Київ"));
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
        assertThat(count("objects")).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\u00a0\u202f", "\u2003"})
    void rejectsClearedNameWithOmittedAddress(String value) throws Exception {
        var body = body();
        body.remove("address");
        body.put("name", value);
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].message").value("Fill in the object name or address"));
        assertThat(count("objects")).isZero();
        verifyNoInteractions(geocoder);
    }

    @Test
    void keepsFullAddressWhenItIsLongerThanThePreviousNameLimit() throws Exception {
        var body = body();
        body.remove("name");
        body.put("address", "А".repeat(500));
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("А".repeat(500)));
        assertThat(jdbc.queryForObject("select name from objects", String.class))
                .hasSize(500);
    }
}
