package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ObjectImportRowDecoder implements ObjectImportDecoder {
    private static final Set<String> NUMBERS = Set.of(
            "longitude",
            "latitude",
            "objectArea",
            "landArea",
            "buildingArea",
            "firstFloorArea",
            "secondFloorArea",
            "basementArea",
            "salesHallArea",
            "floorSlabHeight",
            "connectedPower",
            "waterSupplyFlow",
            "fixedRentAmount",
            "rentIndexationPercent",
            "turnoverPaymentPercent",
            "contractDurationYears");
    private final ObjectMapper json;
    private final ObjectRequestParser parser;

    ObjectImportRowDecoder(ObjectMapper json, ObjectRequestParser parser) {
        this.json = json;
        this.parser = parser;
    }

    public ObjectImport.Command decode(ObjectImport.Input row) {
        ObjectNode body = json.createObjectNode();
        var errors = new ArrayList<FieldViolation>();
        var dates = new HashMap<String, LocalDate>();
        row.values().forEach((path, raw) -> {
            String value = raw.strip();
            if (value.isEmpty()) return;
            if (path.endsWith("PlannedDate")
                    || path.equals("preliminaryContractSignedDate")
                    || path.equals("commissioningDate")) {
                try {
                    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                        throw new DateTimeParseException("Invalid date", value, 0);
                    dates.put(path, LocalDate.parse(value));
                } catch (DateTimeParseException e) {
                    errors.add(new FieldViolation(path, "Use a valid date in yyyy-MM-dd format"));
                }
                return;
            }
            String[] parts = path.split("\\.");
            ObjectNode target = parts.length == 1 ? body : body.withObject("/" + parts[0]);
            String field = parts[parts.length - 1];
            try {
                if (NUMBERS.contains(field)) {
                    if (value.length() > 40) throw new NumberFormatException();
                    var number = new BigDecimal(value);
                    if (number.precision() > 20 || number.scale() < -20 || number.scale() > 20)
                        throw new NumberFormatException();
                    target.put(field, number);
                } else if (field.equals("constructionTermDays")) target.put(field, Integer.parseInt(value));
                else target.put(field, value);
            } catch (NumberFormatException e) {
                errors.add(new FieldViolation(path, "Invalid number"));
            }
        });
        if (!errors.isEmpty()) throw new UnprocessableInputException(errors);
        var object = parser.parse(body).command();
        var milestones = new ObjectMilestones(
                        null,
                        dates.get("constructionAcceptancePlannedDate"),
                        null,
                        dates.get("openingPlannedDate"),
                        null,
                        dates.get("paymentActPlannedDate"),
                        null,
                        dates.get("preliminaryContractSignedDate"),
                        dates.get("commissioningDate"))
                .calculated(
                        object.commercialTerms() == null
                                ? null
                                : object.commercialTerms().constructionTermDays());
        if (dates.containsKey("openingPlannedDate")
                && !java.util.Objects.equals(dates.get("openingPlannedDate"), milestones.openingPlannedDate()))
            throw new UnprocessableInputException(
                    "openingPlannedDate", "Opening plan is calculated from construction acceptance and term");
        return new ObjectImport.Command(object, milestones);
    }
}
