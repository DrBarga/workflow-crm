package dev.workflowcrm.objects.api;

import java.util.List;

public final class ObjectImportTemplate {
    public static final int VERSION = 2;

    private ObjectImportTemplate() {}

    public static final List<String> COLUMNS = List.of(
            "name",
            "objectType",
            "address",
            "regionId",
            "managerId",
            "entrance.longitude",
            "entrance.latitude",
            "objectArea",
            "landArea",
            "contact.name",
            "contact.role",
            "contact.phone",
            "contact.email",
            "technicalSpecifications.buildingArea",
            "technicalSpecifications.firstFloorArea",
            "technicalSpecifications.secondFloorArea",
            "technicalSpecifications.basementArea",
            "technicalSpecifications.salesHallArea",
            "technicalSpecifications.floorSlabHeight",
            "technicalSpecifications.connectedPower",
            "technicalSpecifications.waterSupplyFlow",
            "technicalSpecifications.powerConnectionCategory",
            "technicalSpecifications.sewageType",
            "commercialTerms.leaseTerms",
            "commercialTerms.fixedRentAmount",
            "commercialTerms.rentIndexationPercent",
            "commercialTerms.turnoverPaymentPercent",
            "commercialTerms.counterpartyTaxStatus",
            "commercialTerms.constructionTermDays",
            "commercialTerms.contractDurationYears",
            "constructionAcceptancePlannedDate",
            "paymentActPlannedDate",
            "preliminaryContractSignedDate",
            "commissioningDate",
            "shortComment");
}
