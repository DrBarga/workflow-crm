package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import org.springframework.stereotype.Component;

@Component
class ObjectEditPreparation {
    record Prepared(
            String name,
            String address,
            ObjectType type,
            PropertyAreas areas,
            TechnicalSpecifications technical,
            CommercialTerms commercial,
            String technicalDescription,
            String commercialDescription,
            String legacyContractType,
            ContractType contractType,
            ObjectMilestones milestones,
            ObjectDataSource dataSource,
            String shortComment) {
        void apply(RealEstateObject object) {
            object.rename(name);
            object.editLocation(address, type);
            object.resize(areas);
            object.updateSpecifications(technical, commercial);
            var previous = object.getSpecifications().getContractTypeReference();
            if (contractType != null
                    && (previous == null || !contractType.getId().equals(previous.getId())))
                object.changeContractType(contractType);
            object.describeTechnicalSpecifications(technicalDescription);
            object.describeCommercialTerms(commercialDescription, legacyContractType);
            object.schedule(milestones);
            object.selectDataSource(dataSource);
            object.comment(shortComment);
        }
    }

    private final ObjectEditStore store;
    private final ObjectStatusStore status;

    ObjectEditPreparation(ObjectEditStore store, ObjectStatusStore status) {
        this.store = store;
        this.status = status;
    }

    Prepared prepare(RealEstateObject object, ObjectPatchCommand c) {
        var v = c.values();
        var s = object.getSpecifications();
        String name = ObjectText.required(c.value("name", v.name(), object.getName()), 500, "Object name");
        String address = ObjectText.optional(c.value("address", v.address(), object.getAddress()), 500, "Address");
        ObjectType type = c.fields().contains("objectType")
                ? v.objectType() == null ? null : store.type(v.objectType())
                : object.getType();
        PropertyAreas areas;
        try {
            areas = new PropertyAreas(
                    c.value("objectArea", v.objectArea(), object.getAreas().getObjectArea()),
                    c.value("landArea", v.landArea(), object.getAreas().getLandArea()));
        } catch (IllegalArgumentException e) {
            throw new UnprocessableInputException("objectArea", e.getMessage());
        }
        var contract = c.fields().contains("contractTypeId")
                ? status.contractType(v.contractTypeId())
                : s == null ? null : s.getContractTypeReference();
        boolean changingType = c.fields().contains("contractTypeId")
                && (s == null
                        || s.getContractTypeReference() == null
                        || !s.getContractTypeReference()
                                .getId()
                                .equals(c.values().contractTypeId()));
        var milestones = ObjectEditMilestones.merge(object, c);
        ObjectEditMilestones.protect(object, milestones);
        var dataSource = c.fields().contains("dataSourceId")
                ? v.dataSourceId() == null ? null : store.dataSource(v.dataSourceId())
                : object.getDataSource();
        return new Prepared(
                name,
                address,
                type,
                areas,
                c.value("technicalSpecifications", v.technicalSpecifications(), s == null ? null : s.getTechnical()),
                c.value("commercialTerms", v.commercialTerms(), s == null ? null : s.getCommercial()),
                c.value(
                        "technicalDescription",
                        v.technicalDescription(),
                        s == null ? null : s.getTechnicalSpecifications()),
                c.value("commercialDescription", v.commercialDescription(), s == null ? null : s.getCommercialTerms()),
                changingType || s == null ? null : s.getLegacyContractType(),
                contract,
                milestones,
                dataSource,
                c.value("shortComment", v.shortComment(), object.getShortComment()));
    }
}
