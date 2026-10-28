package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
class JpaObjectEditStore implements ObjectEditStore {
    private final jakarta.persistence.EntityManager entities;
    private final ObjectTypeRepository types;
    private final ObjectHistoryRepository history;
    private final ObjectCardStore cards;
    private final ObjectMapper json;

    JpaObjectEditStore(
            jakarta.persistence.EntityManager entities,
            ObjectTypeRepository types,
            ObjectHistoryRepository history,
            ObjectCardStore cards,
            ObjectMapper json) {
        this.entities = entities;
        this.types = types;
        this.history = history;
        this.cards = cards;
        this.json = json;
    }

    public ObjectType type(String code) {
        return types.findByCode(code)
                .filter(ObjectType::isActive)
                .orElseThrow(() -> new UnprocessableInputException("objectType", "Choose an active object type"));
    }

    public UUID contactId(UUID objectId) {
        return cards.contactId(objectId);
    }

    public ObjectDataSource dataSource(UUID id) {
        return Optional.ofNullable(entities.find(ObjectDataSource.class, id))
                .filter(ObjectDataSource::isActive)
                .orElseThrow(() -> new UnprocessableInputException("dataSourceId", "Choose an active data source"));
    }

    public Map<String, String> snapshot(RealEstateObject o) {
        var s = o.getSpecifications();
        var m = o.getMilestones();
        var value = new ObjectEditValues(
                o.getName(),
                o.getAddress(),
                o.getType() == null ? null : o.getType().getCode(),
                o.getAreas().getObjectArea(),
                o.getAreas().getLandArea(),
                cards.contactId(o.getId()),
                s == null ? null : s.getTechnical(),
                s == null ? null : s.getCommercial(),
                s == null ? null : s.getTechnicalSpecifications(),
                s == null ? null : s.getCommercialTerms(),
                s == null || s.getContractTypeReference() == null
                        ? null
                        : s.getContractTypeReference().getId(),
                o.getContractSignedDate(),
                m == null ? null : m.constructionAcceptancePlannedDate(),
                m == null ? null : m.constructionAcceptanceActualDate(),
                m == null ? null : m.openingPlannedDate(),
                o.getOpeningDate(),
                m == null ? null : m.paymentActPlannedDate(),
                m == null ? null : m.paymentActActualDate(),
                o.getDataSource() == null ? null : o.getDataSource().getId(),
                m == null ? null : m.preliminaryContractSignedDate(),
                m == null ? null : m.commissioningDate(),
                o.getShortComment());
        var result = new LinkedHashMap<String, String>();
        json.valueToTree(value)
                .fields()
                .forEachRemaining(e -> result.put(
                        e.getKey(), e.getValue().isNull() ? null : e.getValue().toString()));
        result.put(
                "entrance",
                json.valueToTree(new CreatedObjectView.Coordinates(
                                o.getEntrance().getLongitude(), o.getEntrance().getLatitude()))
                        .toString());
        result.put("googleMapsUrl", o.getGoogleMapsUrl());
        result.put("legacyContractType", s == null ? null : s.getLegacyContractType());
        return result;
    }

    public void history(UUID actor, RealEstateObject object, Map<String, String> before, Map<String, String> after) {
        before.forEach((field, old) -> {
            if (!Objects.equals(old, after.get(field)))
                history.save(new ObjectHistory(
                        object.getId(),
                        actor,
                        ObjectHistory.ActorType.USER,
                        field,
                        old,
                        after.get(field),
                        object.getVersion()));
        });
    }
}
