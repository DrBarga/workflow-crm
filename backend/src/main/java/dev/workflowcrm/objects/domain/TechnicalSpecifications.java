package dev.workflowcrm.objects.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public record TechnicalSpecifications(
        @Column(name = "building_area", precision = 14, scale = 2) BigDecimal buildingArea,
        @Column(name = "first_floor_area", precision = 14, scale = 2) BigDecimal firstFloorArea,
        @Column(name = "second_floor_area", precision = 14, scale = 2) BigDecimal secondFloorArea,
        @Column(name = "basement_area", precision = 14, scale = 2) BigDecimal basementArea,
        @Column(name = "sales_hall_area", precision = 14, scale = 2) BigDecimal salesHallArea,
        @Column(name = "floor_slab_height", precision = 14, scale = 2) BigDecimal floorSlabHeight,
        @Column(name = "connected_power", precision = 14, scale = 2) BigDecimal connectedPower,
        @Column(name = "water_supply_flow", precision = 14, scale = 2) BigDecimal waterSupplyFlow,
        @Enumerated(EnumType.STRING) @Column(name = "power_connection_category", length = 3)
                PowerConnectionCategory powerConnectionCategory,
        @Enumerated(EnumType.STRING) @Column(name = "sewage_type", length = 32) SewageType sewageType) {
    public TechnicalSpecifications {
        buildingArea = ObjectNumbers.nonnegative(buildingArea, 14, 2, "buildingArea");
        firstFloorArea = ObjectNumbers.nonnegative(firstFloorArea, 14, 2, "firstFloorArea");
        secondFloorArea = ObjectNumbers.nonnegative(secondFloorArea, 14, 2, "secondFloorArea");
        basementArea = ObjectNumbers.nonnegative(basementArea, 14, 2, "basementArea");
        salesHallArea = ObjectNumbers.nonnegative(salesHallArea, 14, 2, "salesHallArea");
        floorSlabHeight = ObjectNumbers.nonnegative(floorSlabHeight, 14, 2, "floorSlabHeight");
        connectedPower = ObjectNumbers.nonnegative(connectedPower, 14, 2, "connectedPower");
        waterSupplyFlow = ObjectNumbers.nonnegative(waterSupplyFlow, 14, 2, "waterSupplyFlow");
    }
}
