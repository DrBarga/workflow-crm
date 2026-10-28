package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.IcDecisionCommand;
import dev.workflowcrm.objects.domain.InvestmentCommitteeDecision;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class IcDecisionParser {
    private final ObjectStatusParser selections;

    IcDecisionParser(ObjectStatusParser selections) {
        this.selections = selections;
    }

    IcDecisionCommand parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("body", "Provide a JSON object");
        var allowed =
                Set.of("decision", "date", "conditions", "refusalComment", "version", "confirmationToken", "assignees");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field, "Unknown field");
        });
        InvestmentCommitteeDecision decision;
        try {
            decision = InvestmentCommitteeDecision.valueOf(text(body, "decision", true));
        } catch (IllegalArgumentException exception) {
            throw invalid("decision", "Choose a valid IC decision");
        }
        LocalDate date;
        try {
            String value = text(body, "date", true);
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("date", "Use YYYY-MM-DD");
            date = LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid("date", "Provide a valid IC date");
        }
        JsonNode version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong())
            throw invalid("version", "Provide the current object version");
        String token = text(body, "confirmationToken", false);
        if (token != null && !token.matches("[a-f0-9]{64}"))
            throw invalid("confirmationToken", "Invalid preview token");
        return new IcDecisionCommand(
                decision,
                date,
                text(body, "conditions", false),
                text(body, "refusalComment", false),
                version.longValue(),
                token,
                selections.assignees(body));
    }

    private String text(JsonNode body, String field, boolean required) {
        var value = body.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw invalid(field, "Field is required");
            return null;
        }
        if (!value.isTextual()) throw invalid(field, "Provide a string");
        return value.textValue();
    }

    private UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
