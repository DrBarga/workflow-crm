package dev.workflowcrm.shared.geography;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeoJsonBoundaryTest {
    final ObjectMapper json = new ObjectMapper();

    @Test
    void includesOuterBoundaryButExcludesHolesAndOutsidePoints() throws Exception {
        var boundary = new GeoJsonBoundary(
                json.readTree(
                        """
                {"type":"Polygon","coordinates":[
                  [[0,0],[10,0],[10,10],[0,10],[0,0]],
                  [[3,3],[7,3],[7,7],[3,7],[3,3]]
                ]}
                """));
        assertThat(boundary.contains(1, 1)).isTrue();
        assertThat(boundary.contains(0, 0)).isTrue();
        assertThat(boundary.contains(0, 5)).isTrue();
        assertThat(boundary.contains(5, 5)).isFalse();
        assertThat(boundary.contains(3, 5)).isFalse();
        assertThat(boundary.contains(-1, 1)).isFalse();
    }

    @Test
    void acceptsSeparatePolygonsAndEitherRingOrientation() throws Exception {
        var boundary = new GeoJsonBoundary(
                json.readTree(
                        """
                {"type":"MultiPolygon","coordinates":[
                  [[[0,0],[0,1],[1,1],[1,0],[0,0]]],
                  [[[2,2],[3,2],[3,3],[2,3],[2,2]]]
                ]}
                """));
        assertThat(boundary.contains(0.5, 0.5)).isTrue();
        assertThat(boundary.contains(2.5, 2.5)).isTrue();
        assertThat(boundary.contains(1.5, 1.5)).isFalse();
    }

    @Test
    void rejectsMissingEmptyUnclosedAndNonNumericGeometry() throws Exception {
        for (var geometry : java.util.List.of(
                "{}",
                "{\"type\":\"Point\",\"coordinates\":[1,1]}",
                "{\"type\":\"Polygon\",\"coordinates\":[]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,1],[1,1],[1,0]]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,1],[181,1],[0,0]]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,1],[\"1\",1],[0,0]]]}")) {
            var node = json.readTree(geometry);
            assertThatThrownBy(() -> new GeoJsonBoundary(node)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
