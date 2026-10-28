package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectListSortingIT extends ObjectListTestSupport {
    @ParameterizedTest
    @CsvSource({
        "createdAt,asc,BCA",
        "createdAt,desc,ACB",
        "name,asc,ABC",
        "name,desc,CBA",
        "status,asc,ACB",
        "status,desc,BCA",
        "investmentCommitteeDate,asc,BAC",
        "investmentCommitteeDate,desc,ABC",
        "contractSignedDate,asc,BAC",
        "contractSignedDate,desc,ABC"
    })
    void everySortWorksInBothDirectionsAcrossPagesWithNullsLast(String sort, String direction, String expected)
            throws Exception {
        UUID a = add("A", manager, region, "2026-01-03T12:00:00Z", "FOUND"),
                b = add("B", manager, region, "2026-01-01T12:00:00Z", "CONTRACT_SIGNED");
        UUID c = add("C", manager, region, "2026-01-02T12:00:00Z", "IN_PROGRESS");
        jdbc.update(
                "update objects set investment_committee_decision='APPROVED', investment_committee_date='2026-01-03',contract_signed_date='2026-01-03',preliminary_contract_signed_date='2025-01-01' where id=?",
                a);
        jdbc.update(
                "update objects set investment_committee_decision='APPROVED', investment_committee_date='2026-01-01',contract_signed_date='2026-01-01',preliminary_contract_signed_date='2027-01-01' where id=?",
                b);
        jdbc.update("update objects set preliminary_contract_signed_date='2024-01-01' where id=?", c);
        var parameters = new HashMap<>(Map.of("sort", sort, "direction", direction, "limit", "1"));
        var observed = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var result = page(director, parameters);
            observed.addAll(names(result));
            assertThat(result.path("hasMore").asBoolean()).isEqualTo(i < 2);
            if (i < 2) parameters.put("cursor", result.path("nextCursor").asText());
            else assertThat(result.path("nextCursor").isNull()).isTrue();
        }
        assertThat(String.join("", observed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void legacyNullNamesRemainLastAndCanBeContinued(String direction) throws Exception {
        add("A", manager, region);
        UUID first = add(null, manager, region), second = add(null, manager, region);
        var parameters = new HashMap<>(Map.of("sort", "name", "direction", direction, "limit", "1"));
        var named = page(director, parameters);
        assertThat(names(named)).containsExactly("A");
        parameters.put("cursor", named.path("nextCursor").asText());
        var middle = page(director, parameters);
        assertThat(middle.path("content").get(0).path("name").isNull()).isTrue();
        parameters.put("cursor", middle.path("nextCursor").asText());
        var last = page(director, parameters);
        assertThat(last.path("content").get(0).path("name").isNull()).isTrue();
        assertThat(last.path("hasMore").asBoolean()).isFalse();
        assertThat(Set.of(
                        middle.path("content").get(0).path("id").asText(),
                        last.path("content").get(0).path("id").asText()))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
    }
}
