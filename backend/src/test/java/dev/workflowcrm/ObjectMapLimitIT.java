package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectMapLimitIT extends ObjectMapTestSupport {
    @Test
    void wideBoundsAreCappedDeterministicallyAfterApplyingBboxRolesAndFilters() throws Exception {
        ObjectListBenchmarkFixture.seed(jdbc, manager, colleague, outsider, region, otherRegion);
        jdbc.update(
                "update objects set entrance_longitude=40,entrance_latitude=48 where substring(name from 9)::int>2000");
        var exact = pins(director);
        assertThat(exact.path("content")).hasSize(2000);
        assertThat(exact.path("limit").asInt()).isEqualTo(2000);
        assertThat(exact.path("truncated").asBoolean()).isFalse();
        var wide = pins(director, "-180,-90,180,90", Map.of());
        assertThat(wide.path("content")).hasSize(2000);
        assertThat(wide.path("truncated").asBoolean()).isTrue();
        assertThat(wide).isEqualTo(pins(director, "-180,-90,180,90", Map.of()));
        var filtered = pins(director, "-180,-90,180,90", Map.of("q", "Магазин 000"));
        assertThat(filtered.path("content")).hasSize(999);
        assertThat(filtered.path("truncated").asBoolean()).isFalse();
        var regional = pins(manager);
        assertThat(regional.path("content")).hasSize(1334);
        assertThat(regional.path("truncated").asBoolean()).isFalse();
        assertThat(fields(wide)).containsExactlyInAnyOrder("content", "limit", "truncated");
    }
}
