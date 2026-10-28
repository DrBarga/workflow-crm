package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectSigningDetails;
import dev.workflowcrm.objects.domain.ObjectStatusCommand;
import dev.workflowcrm.objects.domain.ObjectTaskSetCommand;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusParser {
    ObjectTaskSetCommand taskSet(JsonNode body) {
        if (body != null && body.isObject() && body.has("approvalRequestId")) {
            fields(body, Set.of("approvalRequestId", "confirmationToken", "assignees"));
            UUID request = uuid(body, "approvalRequestId");
            if (request == null) throw invalid("approvalRequestId", "Provide the approval request UUID");
            return new ObjectTaskSetCommand(null, request, token(body), assignees(body));
        }
        var command = parse(body, false);
        return new ObjectTaskSetCommand(command, null, command.confirmationToken());
    }

    ObjectStatusCommand parse(JsonNode body, boolean milestone) {
        fields(
                body,
                milestone
                        ? Set.of(
                                "version",
                                "contractSignedDate",
                                "contractTypeId",
                                "openingDate",
                                "preliminaryContractSignedDate",
                                "commissioningDate",
                                "constructionAcceptancePlannedDate",
                                "constructionTermDays",
                                "openingPlannedDate",
                                "assignees",
                                "confirmationToken")
                        : Set.of(
                                "status",
                                "version",
                                "refusalReasonId",
                                "contractSignedDate",
                                "contractTypeId",
                                "openingDate",
                                "preliminaryContractSignedDate",
                                "commissioningDate",
                                "constructionAcceptancePlannedDate",
                                "constructionTermDays",
                                "openingPlannedDate",
                                "assignees",
                                "confirmationToken"));
        String status = milestone ? null : text(body, "status");
        if (!milestone && (status == null || !status.matches("[A-Z][A-Z0-9_]{0,63}")))
            throw invalid("status", "Choose a target status");
        var version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw invalid("version", "Provide the current nonnegative object version");
        return new ObjectStatusCommand(
                status,
                version.longValue(),
                uuid(body, "refusalReasonId"),
                date(body, "contractSignedDate"),
                uuid(body, "contractTypeId"),
                date(body, "openingDate"),
                date(body, "openingPlannedDate"),
                body.has("openingPlannedDate"),
                token(body),
                signing(body),
                assignees(body));
    }

    java.util.Map<UUID, UUID> assignees(JsonNode body) {
        var value = body.path("assignees");
        if (value.isMissingNode()) return java.util.Map.of();
        if (!value.isObject()) throw invalid("assignees", "Provide template item UUIDs mapped to user UUIDs");
        var result = new java.util.TreeMap<UUID, UUID>();
        value.fields().forEachRemaining(entry -> {
            UUID item;
            try {
                item = UUID.fromString(entry.getKey());
                if (!item.toString().equalsIgnoreCase(entry.getKey())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                throw invalid("assignees", "Provide a template item UUID");
            }
            UUID user = uuid(value, entry.getKey());
            if (user == null) throw invalid("assignees", "Choose an assignee for each selected item");
            if (result.putIfAbsent(item, user) != null) throw invalid("assignees", "Duplicate template item");
        });
        return result;
    }

    private ObjectSigningDetails signing(JsonNode body) {
        var supplied = new java.util.HashSet<String>();
        for (String field : Set.of(
                "preliminaryContractSignedDate",
                "commissioningDate",
                "constructionAcceptancePlannedDate",
                "constructionTermDays")) if (body.has(field)) supplied.add(field);
        if (supplied.isEmpty()) return null;
        Integer days = null;
        if (body.hasNonNull("constructionTermDays")) {
            var value = body.path("constructionTermDays");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0)
                throw invalid("constructionTermDays", "Provide a nonnegative integer");
            days = value.intValue();
        }
        return new ObjectSigningDetails(
                date(body, "preliminaryContractSignedDate"),
                date(body, "commissioningDate"),
                date(body, "constructionAcceptancePlannedDate"),
                days,
                supplied);
    }

    String approval(JsonNode body) {
        fields(body, Set.of("confirmationToken", "assignees"));
        return token(body);
    }

    private void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("body", "Provide a JSON object");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field, "Unknown field");
        });
    }

    private String text(JsonNode body, String field) {
        var value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw invalid(field, "Provide a string");
        return value.textValue();
    }

    private LocalDate date(JsonNode body, String field) {
        String value = text(body, field);
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(field, "Use YYYY-MM-DD");
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalid(field, "Provide a valid date");
        }
    }

    private UUID uuid(JsonNode body, String field) {
        String value = text(body, field);
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw invalid(field, "Provide a UUID");
            return id;
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "Provide a UUID");
        }
    }

    private String token(JsonNode body) {
        String token = text(body, "confirmationToken");
        if (token != null && !token.matches("[a-f0-9]{64}"))
            throw invalid("confirmationToken", "Invalid preview token");
        return token;
    }

    private UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
