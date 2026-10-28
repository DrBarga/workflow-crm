package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectCreationBoundaryIT extends ObjectApiTestSupport {
    @Test
    void noLoadedBoundariesRequiresManualRegionEvenWhenGeocoderCouldSuggestOne() throws Exception {
        var body = body();
        body.remove("regionId");
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("regionId"));
        assertThat(count("objects")).isZero();
        verifyNoInteractions(geocoder);
        body.put("regionId", region.toString());
        create(manager, key(), body).andExpect(status().isCreated());
    }

    @Test
    void loadedPolygonDeterminesRegionIndependentlyOfGeocoderHints() throws Exception {
        ObjectRegionBoundaryFixture.load(jdbc, region);
        var body = body();
        body.remove("regionId");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionId").value(region.toString()));
        verifyNoInteractions(geocoder);
    }

    @Test
    void ambiguousBoundariesRequireManualSelectionAndDoNotOverrideIt() throws Exception {
        ObjectRegionBoundaryFixture.load(jdbc, region);
        ObjectRegionBoundaryFixture.load(jdbc, otherRegion);
        var body = body();
        body.remove("regionId");
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        body.put("regionId", region.toString());
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionId").value(region.toString()));
    }

    @Test
    void automaticRegionOutsideMembershipIsForbiddenButManualOverrideRemainsAvailable() throws Exception {
        ObjectRegionBoundaryFixture.load(jdbc, otherRegion);
        var body = body();
        body.remove("regionId");
        create(manager, key(), body).andExpect(status().isForbidden());
        body.put("regionId", region.toString());
        create(manager, key(), body).andExpect(status().isCreated());
    }

    @Test
    void malformedOrNonMatchingLoadedGeometryFallsBackToManualSelection() throws Exception {
        jdbc.update(
                "insert into region_boundaries(region_id,geometry) values(?,?::jsonb)",
                region,
                "{\"type\":\"Polygon\",\"coordinates\":[]}");
        var body = body();
        body.remove("regionId");
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        jdbc.update(
                "update region_boundaries set geometry=?::jsonb where region_id=?",
                "{\"type\":\"Polygon\",\"coordinates\":[[[35,48],[36,48],[36,49],[35,49],[35,48]]]}",
                region);
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        assertThat(count("objects")).isZero();
    }
}
