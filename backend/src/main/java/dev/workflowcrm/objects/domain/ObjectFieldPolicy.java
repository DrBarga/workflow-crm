package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ObjectFieldPolicy {
    static final Set<String> CONTRACT_FIELDS = Set.of(
            "contractTypeId",
            "contractSignedDate",
            "preliminaryContractSignedDate",
            "commissioningDate",
            "constructionAcceptancePlannedDate",
            "constructionAcceptanceActualDate");
    private static final Set<String> GENERAL =
            Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN");

    Map<String, Boolean> editable(ObjectAssignmentQuery.Member actor, boolean hasContractTypeTaskSet) {
        String role = actor.role();
        boolean general = GENERAL.contains(role);
        boolean contract = contractEditable(role, hasContractTypeTaskSet);
        var result = new LinkedHashMap<String, Boolean>();
        for (String field : new String[] {
            "name",
            "dataSourceId",
            "objectArea",
            "landArea",
            "contactId",
            "technicalSpecifications",
            "commercialTerms",
            "technicalDescription",
            "commercialDescription",
            "shortComment",
            "openingActualDate",
            "paymentActPlannedDate",
            "paymentActActualDate"
        }) result.put(field, general);
        result.put("address", general);
        result.put("objectType", general);
        for (String field : CONTRACT_FIELDS)
            result.put(field, field.equals("contractTypeId") ? contract : general || role.equals("CONTRACT_MANAGER"));
        result.put("commissioningDate", (general && !role.equals("MANAGER")) || role.equals("CONTRACT_MANAGER"));
        for (String field : new String[] {
            "id",
            "regionId",
            "managerId",
            "authorId",
            "createdAt",
            "updatedAt",
            "status",
            "entrance",
            "googleMapsUrl",
            "openingPlannedDate",
            "geoM",
            "version"
        }) result.put(field, false);
        return Map.copyOf(result);
    }

    boolean contractEditable(String role, boolean hasContractTypeTaskSet) {
        return role.equals("SUPERADMIN")
                || !hasContractTypeTaskSet && (GENERAL.contains(role) || role.equals("CONTRACT_MANAGER"));
    }

    int effectiveOrder(RealEstateObject o) {
        var status = o.getStatus();
        if ("PAUSED".equals(status.getCode()) && o.getStatusBeforePause() != null) status = o.getStatusBeforePause();
        if ("REFUSED".equals(status.getCode()) && o.getStatusBeforeRefusal() != null)
            status = o.getStatusBeforeRefusal();
        return status.getSortOrder();
    }
}
