package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectListRegionalCursorIT extends ObjectListTestSupport {
    @ParameterizedTest
    @CsvSource({
        "createdAt,asc",
        "createdAt,desc",
        "name,asc",
        "name,desc",
        "status,asc",
        "status,desc",
        "investmentCommitteeDate,asc",
        "investmentCommitteeDate,desc",
        "contractSignedDate,asc",
        "contractSignedDate,desc"
    })
    void allSortsPaginateMixedFullAndRestrictedRowsWithoutDuplicates(String sort, String direction) throws Exception {
        add("A", manager, region, "2026-01-14T12:00:00Z", "FOUND");
        var foreign = add("B", outsider, otherRegion, "2026-01-15T12:00:00Z", "IN_PROGRESS");
        var own = add("C", colleague, region, "2026-01-16T12:00:00Z", "REFUSED");
        for (UUID id : List.of(foreign, own))
            jdbc.update(
                    "update objects set investment_committee_date='2026-01-20',investment_committee_decision='APPROVED',contract_signed_date='2026-01-21' where id=?",
                    id);
        var parameters = new HashMap<>(Map.of("sort", sort, "direction", direction, "limit", "1"));
        var expected = names(page(director, Map.of("sort", sort, "direction", direction)));
        var actual = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var response = page(lead, parameters);
            assertThat(response.path("content")).hasSize(1);
            var row = response.path("content").get(0);
            actual.add(row.path("name").asText());
            if (row.path("name").asText().equals("B"))
                assertThat(fields(row)).containsExactlyInAnyOrder("name", "entrance");
            else assertThat(row.has("workflow")).isTrue();
            assertThat(response.path("hasMore").asBoolean()).isEqualTo(i < 2);
            if (i < 2) parameters.put("cursor", response.path("nextCursor").asText());
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }
}
