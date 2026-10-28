package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectListFilter;
import dev.workflowcrm.objects.domain.ObjectListPosition;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

record ObjectListOrder(String column, String type, String direction) {
    static ObjectListOrder of(ObjectListFilter filter) {
        String direction = filter.direction().name();
        return switch (filter.sort()) {
            case CREATED_AT -> new ObjectListOrder("o.created_at", "timestamptz", direction);
            case NAME -> new ObjectListOrder("lower(o.name)", "text", direction);
            case STATUS -> new ObjectListOrder("s.sort_order", "integer", direction);
            case INVESTMENT_COMMITTEE_DATE -> new ObjectListOrder("o.investment_committee_date", "date", direction);
            case CONTRACT_SIGNED_DATE -> new ObjectListOrder("o.contract_signed_date", "date", direction);
        };
    }

    String ordering() {
        return column + " " + direction + " nulls last, o.id " + direction;
    }

    String after(ObjectListPosition position, MapSqlParameterSource parameters) {
        if (position.id() == null) return "";
        String compare = direction.equals("ASC") ? ">" : "<";
        parameters.addValue("afterId", position.id());
        if (position.value() == null) return " and (" + column + " is null and o.id " + compare + " :afterId)";
        parameters.addValue("afterValue", position.value());
        String value = "cast(:afterValue as " + type + ")";
        return " and (" + column + " " + compare + " " + value + " or (" + column + " = " + value + " and o.id "
                + compare + " :afterId) or " + column + " is null)";
    }
}
