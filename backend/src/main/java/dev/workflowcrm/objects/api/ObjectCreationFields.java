package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.shared.error.FieldViolation;
import java.util.List;
import java.util.Set;

final class ObjectCreationFields {
    private ObjectCreationFields() {}

    static void check(JsonNode body, List<FieldViolation> errors) {
        fields(
                body,
                "",
                Set.of(
                        "name",
                        "objectType",
                        "address",
                        "regionId",
                        "entrance",
                        "objectArea",
                        "landArea",
                        "contact",
                        "contactId",
                        "managerId",
                        "dataSourceId",
                        "shortComment",
                        "confirmDuplicates",
                        "technicalSpecifications",
                        "commercialTerms"),
                errors);
        if (body != null && body.isObject()) {
            if ((body.has("address") || body.has("name")) && blank(body.get("address")) && blank(body.get("name")))
                errors.add(new FieldViolation("name", "Fill in the object name or address"));
            nested(body, "entrance", Set.of("longitude", "latitude"), errors);
            nested(body, "contact", Set.of("name", "role", "phone", "email"), errors);
            nested(
                    body,
                    "technicalSpecifications",
                    Set.of(
                            "buildingArea",
                            "firstFloorArea",
                            "secondFloorArea",
                            "basementArea",
                            "salesHallArea",
                            "floorSlabHeight",
                            "connectedPower",
                            "waterSupplyFlow",
                            "powerConnectionCategory",
                            "sewageType"),
                    errors);
            nested(
                    body,
                    "commercialTerms",
                    Set.of(
                            "leaseTerms",
                            "fixedRentAmount",
                            "rentIndexationPercent",
                            "turnoverPaymentPercent",
                            "counterpartyTaxStatus",
                            "constructionTermDays",
                            "contractDurationYears"),
                    errors);
        }
        text(body, "", errors);
    }

    private static void nested(JsonNode body, String field, Set<String> allowed, List<FieldViolation> errors) {
        if (body.hasNonNull(field)) fields(body.get(field), field, allowed, errors);
    }

    private static void fields(JsonNode value, String prefix, Set<String> allowed, List<FieldViolation> errors) {
        if (value == null || !value.isObject()) {
            errors.add(new FieldViolation(prefix.isEmpty() ? "body" : prefix, "Must be a JSON object"));
            return;
        }
        value.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field))
                errors.add(new FieldViolation(path(prefix, field), "Field is not accepted when creating an object"));
        });
    }

    private static void text(JsonNode value, String path, List<FieldViolation> errors) {
        if (value == null) return;
        if (value.isObject())
            value.fields().forEachRemaining(entry -> text(entry.getValue(), path(path, entry.getKey()), errors));
        else if (value.isTextual()
                && (value.asText().contains("<")
                        || value.asText().contains(">")
                        || value.asText().codePoints().anyMatch(Character::isISOControl)))
            errors.add(new FieldViolation(path, "Text must not contain markup or control characters"));
    }

    private static String path(String prefix, String field) {
        return prefix.isEmpty() ? field : prefix + "." + field;
    }

    private static boolean blank(JsonNode value) {
        return value == null
                || value.isNull()
                || value.isTextual()
                        && value.asText()
                                .codePoints()
                                .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
