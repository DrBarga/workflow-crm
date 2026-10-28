package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectListScope;
import java.util.List;

record ObjectExportColumns(String name, String sql, boolean pin, boolean contract) {
    ObjectExportColumns(String name, String sql) {
        this(name, sql, false, false);
    }

    static List<ObjectExportColumns> forScope(ObjectListScope scope) {
        return ALL.stream()
                .filter(c -> !c.contract() || !scope.role().equals("SUPERADMIN"))
                .toList();
    }

    private static final List<ObjectExportColumns> ALL = List.of(
            new ObjectExportColumns("id", "o.id", true, false),
            new ObjectExportColumns("name", "o.name", true, false),
            new ObjectExportColumns("entrance.longitude", "o.entrance_longitude", true, false),
            new ObjectExportColumns("entrance.latitude", "o.entrance_latitude", true, false),
            new ObjectExportColumns("status", "s.code"),
            new ObjectExportColumns("statusName", "s.name"),
            new ObjectExportColumns("objectType", "t.code"),
            new ObjectExportColumns("address", "o.address"),
            new ObjectExportColumns("regionId", "o.region_id"),
            new ObjectExportColumns("managerId", "o.manager_id"),
            new ObjectExportColumns("authorId", "o.author_id"),
            new ObjectExportColumns("createdAt", "o.created_at"),
            new ObjectExportColumns("updatedAt", "o.updated_at"),
            new ObjectExportColumns("version", "o.version"),
            new ObjectExportColumns("objectArea", "o.object_area"),
            new ObjectExportColumns("landArea", "o.land_area"),
            new ObjectExportColumns("technicalSpecifications.buildingArea", "o.building_area"),
            new ObjectExportColumns("technicalSpecifications.firstFloorArea", "o.first_floor_area"),
            new ObjectExportColumns("technicalSpecifications.secondFloorArea", "o.second_floor_area"),
            new ObjectExportColumns("technicalSpecifications.basementArea", "o.basement_area"),
            new ObjectExportColumns("technicalSpecifications.salesHallArea", "o.sales_hall_area"),
            new ObjectExportColumns("technicalSpecifications.floorSlabHeight", "o.floor_slab_height"),
            new ObjectExportColumns("technicalSpecifications.connectedPower", "o.connected_power"),
            new ObjectExportColumns("technicalSpecifications.waterSupplyFlow", "o.water_supply_flow"),
            new ObjectExportColumns("technicalSpecifications.powerConnectionCategory", "o.power_connection_category"),
            new ObjectExportColumns("technicalSpecifications.sewageType", "o.sewage_type"),
            new ObjectExportColumns("technicalDescription", "left(o.technical_specifications,1048577)"),
            new ObjectExportColumns("commercialTerms.leaseTerms", "left(o.lease_terms,1048577)"),
            new ObjectExportColumns("commercialTerms.fixedRentAmount", "o.fixed_rent_amount"),
            new ObjectExportColumns("commercialTerms.rentIndexationPercent", "o.rent_indexation_percent"),
            new ObjectExportColumns("commercialTerms.turnoverPaymentPercent", "o.turnover_payment_percent"),
            new ObjectExportColumns("commercialTerms.counterpartyTaxStatus", "o.counterparty_tax_status"),
            new ObjectExportColumns("commercialTerms.constructionTermDays", "o.construction_term_days"),
            new ObjectExportColumns("commercialTerms.contractDurationYears", "o.contract_duration_years"),
            new ObjectExportColumns("commercialDescription", "left(o.commercial_terms,1048577)"),
            new ObjectExportColumns("contractType", "coalesce(ct.code,o.contract_type)", false, true),
            new ObjectExportColumns("contractTypeId", "o.contract_type_id", false, true),
            new ObjectExportColumns("statusBeforePause", "paused.code"),
            new ObjectExportColumns("statusBeforeRefusal", "refused.code"),
            new ObjectExportColumns("investmentCommitteeDecision", "o.investment_committee_decision"),
            new ObjectExportColumns("investmentCommitteeDate", "o.investment_committee_date"),
            new ObjectExportColumns("investmentCommitteeConditions", "o.investment_committee_conditions"),
            new ObjectExportColumns("investmentCommitteeRefusalComment", "o.investment_committee_refusal_comment"),
            new ObjectExportColumns("refusalReason", "reason.code"),
            new ObjectExportColumns("shortComment", "o.short_comment"),
            new ObjectExportColumns("preliminaryContractSignedDate", "o.preliminary_contract_signed_date"),
            new ObjectExportColumns("commissioningDate", "o.commissioning_date"),
            new ObjectExportColumns("contractSignedDate", "o.contract_signed_date"),
            new ObjectExportColumns("constructionAcceptancePlannedDate", "o.construction_acceptance_planned_date"),
            new ObjectExportColumns("constructionAcceptanceActualDate", "o.construction_acceptance_actual_date"),
            new ObjectExportColumns("openingPlannedDate", "o.opening_planned_date"),
            new ObjectExportColumns("openingActualDate", "o.opening_date"),
            new ObjectExportColumns("paymentActPlannedDate", "o.payment_act_planned_date"),
            new ObjectExportColumns("paymentActActualDate", "o.payment_act_actual_date"),
            new ObjectExportColumns(
                    "googleMapsUrl",
                    "'https://www.google.com/maps/search/?api=1&query='||o.entrance_latitude||','||o.entrance_longitude"));
}
