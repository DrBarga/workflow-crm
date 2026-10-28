package dev.workflowcrm.objects.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.EntranceCoordinates;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UkraineBoundaryTest {
    @ParameterizedTest
    @CsvSource({
        "30.5234,50.4501,true",
        "35.0462,48.4647,true",
        "24.0316,49.8429,true",
        "34.1003,44.9521,true",
        "37.8028,48.0159,true",
        "36.2304,49.9935,true",
        "28.8361,45.3517,true",
        "21.6950,48.4464,false",
        "27.5550,53.9000,false",
        "28.8638,47.0105,false",
        "40.0,49.0,false",
        "0,0,false"
    })
    void checksNationalBoundaryIncludingCrimeaAndExcludesNeighbouringCountries(
            String longitude, String latitude, boolean expected) throws Exception {
        var boundary = new GeoJsonUkraineBoundary(new ObjectMapper());
        assertThat(boundary.contains(new EntranceCoordinates(new BigDecimal(longitude), new BigDecimal(latitude))))
                .isEqualTo(expected);
    }
}
