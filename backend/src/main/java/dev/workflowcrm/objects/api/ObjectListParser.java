package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectListCursors;
import dev.workflowcrm.objects.domain.ObjectListFilter;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
class ObjectListParser {
    private static final Set<String> FIELDS = Set.of(
            "status",
            "managerId",
            "regionId",
            "objectType",
            "q",
            "createdFrom",
            "createdTo",
            "sort",
            "direction",
            "limit",
            "cursor");
    private static final Map<String, ObjectListFilter.Sort> SORTS = Map.of(
            "createdAt",
            ObjectListFilter.Sort.CREATED_AT,
            "name",
            ObjectListFilter.Sort.NAME,
            "status",
            ObjectListFilter.Sort.STATUS,
            "investmentCommitteeDate",
            ObjectListFilter.Sort.INVESTMENT_COMMITTEE_DATE,
            "contractSignedDate",
            ObjectListFilter.Sort.CONTRACT_SIGNED_DATE);

    record Request(ObjectListFilter filter, String cursor) {}

    Request parse(MultiValueMap<String, String> values) {
        values.forEach((field, v) -> {
            if (!FIELDS.contains(field)) throw invalid(field);
            if (v.size() != 1 || v.getFirst().isBlank()) throw invalid(field);
        });
        String sort = values.getFirst("sort");
        var sorting = sort == null ? ObjectListFilter.Sort.CREATED_AT : SORTS.get(sort);
        if (sorting == null) throw invalid("sort");
        var direction = read(values, "direction", s -> ObjectListFilter.Direction.valueOf(s.toUpperCase(Locale.ROOT)));
        var limit = read(values, "limit", Integer::valueOf);
        if (limit != null && (limit < 1 || limit > 100)) throw invalid("limit");
        var from = read(values, "createdFrom", LocalDate::parse);
        var to = read(values, "createdTo", LocalDate::parse);
        if (from != null && to != null && from.isAfter(to)) throw invalid("createdTo");
        if (from != null && (from.getYear() < 1 || from.getYear() > 9998)) throw invalid("createdFrom");
        if (to != null && (to.getYear() < 1 || to.getYear() > 9998)) throw invalid("createdTo");
        String q = text(values, "q", 200);
        String cursor = text(values, "cursor", ObjectListCursors.MAX_LENGTH);
        return new Request(
                new ObjectListFilter(
                        code(values, "status"),
                        read(values, "managerId", ObjectListParser::uuid),
                        read(values, "regionId", ObjectListParser::uuid),
                        code(values, "objectType"),
                        q,
                        from,
                        to,
                        sorting,
                        direction == null ? ObjectListFilter.Direction.DESC : direction,
                        limit == null ? 50 : limit),
                cursor);
    }

    private String text(MultiValueMap<String, String> values, String field, int max) {
        String value = values.getFirst(field);
        if (value == null) return null;
        value = value.strip();
        if (value.length() > max || value.chars().anyMatch(Character::isISOControl)) throw invalid(field);
        return value;
    }

    private String code(MultiValueMap<String, String> values, String field) {
        String value = text(values, field, 64);
        if (value == null) return null;
        value = value.toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid(field);
        return value;
    }

    private <T> T read(MultiValueMap<String, String> values, String field, Function<String, T> parse) {
        String value = values.getFirst(field);
        if (value == null) return null;
        try {
            return parse.apply(value);
        } catch (RuntimeException e) {
            throw invalid(field);
        }
    }

    private static UnprocessableInputException invalid(String field) {
        return new UnprocessableInputException(field, "Invalid list parameter");
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }
}
