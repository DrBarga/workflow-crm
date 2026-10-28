package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectCardService {
    private final ObjectCardAccess access;
    private final ObjectCardStore store;
    private final ObjectCardTransitions transitions;
    private final ObjectCardRequests requests;
    private final ObjectFieldPolicy fields;
    private final ObjectTaskTransition tasks;

    ObjectCardService(
            ObjectCardAccess access,
            ObjectCardStore store,
            ObjectCardTransitions transitions,
            ObjectCardRequests requests,
            ObjectFieldPolicy fields,
            ObjectTaskTransition tasks) {
        this.access = access;
        this.store = store;
        this.transitions = transitions;
        this.requests = requests;
        this.fields = fields;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectCard get(UUID actorId, UUID objectId) {
        var actor = access.require(actorId);
        var summary = store.summary(objectId);
        boolean full = access.full(actor, summary);
        if (!full && !actor.role().equals("CONTRACT_MANAGER"))
            return new ObjectCard.Pin(summary.name(), summary.entrance());
        var object = store.full(objectId);
        boolean hasTasks = tasks.hasTasks(objectId);
        var available = transitions.available(actor, object, hasTasks);
        var pending = requests.pending(objectId);
        var editable = fields.editable(actor, tasks.hasContractTypeTaskSet(objectId));
        var specifications = object.getSpecifications();
        var milestones = object.getMilestones();
        String technical = specifications == null ? null : specifications.getTechnicalSpecifications();
        if (!full)
            return new ObjectCard.Technical(
                    summary.name(),
                    summary.entrance(),
                    specifications == null ? null : specifications.getTechnical(),
                    technical,
                    specifications == null ? null : specifications.getContractType(),
                    specifications == null || specifications.getContractTypeReference() == null
                            ? null
                            : specifications.getContractTypeReference().getId(),
                    object.getContractSignedDate(),
                    milestones == null ? null : milestones.constructionAcceptancePlannedDate(),
                    milestones == null ? null : milestones.constructionAcceptanceActualDate(),
                    milestones == null ? null : milestones.preliminaryContractSignedDate(),
                    milestones == null ? null : milestones.commissioningDate(),
                    milestones == null ? null : milestones.openingPlannedDate(),
                    object.getVersion(),
                    object.getGoogleMapsUrl(),
                    available,
                    pending,
                    editable);
        return new ObjectCard.Full(
                CreatedObjectView.forList(object, store.contactId(objectId)),
                ObjectCardWorkflow.of(object, access.contractVisible(actor)),
                technical,
                specifications == null ? null : specifications.getCommercialTerms(),
                available,
                pending,
                editable);
    }
}
