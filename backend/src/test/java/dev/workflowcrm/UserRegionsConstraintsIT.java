package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UserRegionsConstraintsIT extends ObjectApiTestSupport {
    @Test
    void enforcesMembershipReferencesUniquenessAndCascadesIndependentAssignments() {
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", colleague, otherRegion);
        assertThatThrownBy(() ->
                        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", colleague, otherRegion))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into user_regions(user_id,region_id) values(?,?)", UUID.randomUUID(), otherRegion))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into user_regions(user_id,region_id) values(?,?)", colleague, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from users where id=?", colleague);
        assertThat(jdbc.queryForObject("select count(*) from user_regions where user_id=?", Integer.class, colleague))
                .isZero();
    }

    @Test
    void requiresKnownGeoJsonShapeAndOnlyOneBoundaryPerRegion() {
        for (var geometry : java.util.List.of("{}", "null", "{\"type\":\"Point\",\"coordinates\":[30,50]}"))
            assertThatThrownBy(() -> jdbc.update(
                            "insert into region_boundaries(region_id,geometry) values(?,?::jsonb)", region, geometry))
                    .isInstanceOf(DataIntegrityViolationException.class);
        ObjectRegionBoundaryFixture.load(jdbc, region);
        assertThatThrownBy(() -> ObjectRegionBoundaryFixture.load(jdbc, region))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ObjectRegionBoundaryFixture.load(jdbc, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
