package dev.workflowcrm.objects.domain;

import java.util.UUID;

public interface IcDecisionStore {
    RealEstateObject lock(UUID id);

    ObjectStatusTransition transition(RealEstateObject object, InvestmentCommitteeDecision decision);

    void save(RealEstateObject object);

    void history(UUID actor, RealEstateObject object, String previousDecision, String decision, String previousStatus);
}
