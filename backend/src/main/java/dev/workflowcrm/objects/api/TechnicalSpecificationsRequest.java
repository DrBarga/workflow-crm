package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.PowerConnectionCategory;
import dev.workflowcrm.objects.domain.SewageType;
import dev.workflowcrm.objects.domain.TechnicalSpecifications;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

record TechnicalSpecificationsRequest(
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal buildingArea,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal firstFloorArea,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal secondFloorArea,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal basementArea,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal salesHallArea,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal floorSlabHeight,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal connectedPower,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal waterSupplyFlow,
        PowerConnectionCategory powerConnectionCategory,
        SewageType sewageType) {
    TechnicalSpecificationsRequest {
        buildingArea = number(buildingArea);
        firstFloorArea = number(firstFloorArea);
        secondFloorArea = number(secondFloorArea);
        basementArea = number(basementArea);
        salesHallArea = number(salesHallArea);
        floorSlabHeight = number(floorSlabHeight);
        connectedPower = number(connectedPower);
        waterSupplyFlow = number(waterSupplyFlow);
    }

    TechnicalSpecifications value() {
        return new TechnicalSpecifications(
                buildingArea,
                firstFloorArea,
                secondFloorArea,
                basementArea,
                salesHallArea,
                floorSlabHeight,
                connectedPower,
                waterSupplyFlow,
                powerConnectionCategory,
                sewageType);
    }

    private static BigDecimal number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
