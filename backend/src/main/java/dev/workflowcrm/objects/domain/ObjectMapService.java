package dev.workflowcrm.objects.domain;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectMapService {
    public static final int MAX_RESULTS = 2000;
    private final ObjectListAccess access;
    private final ObjectMapStore store;
    private final Clock clock;

    public ObjectMapService(ObjectListAccess access, ObjectMapStore store, Clock clock) {
        this.access = access;
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectMapResult find(UUID actor, ObjectListFilter filter, ObjectMapBounds bounds) {
        var scope = access.require(actor);
        if (scope.regionRestricted() && scope.regionIds().isEmpty())
            return new ObjectMapResult(List.of(), MAX_RESULTS, false);
        var rows = store.find(filter, scope, bounds, clock.instant(), MAX_RESULTS + 1);
        var pins = rows.stream()
                .limit(MAX_RESULTS)
                .map(r -> new ObjectMapPin(
                        r.id(), r.name(), r.entrance(), scope.full(r.managerId(), r.regionId()) ? r.status() : null))
                .toList();
        return new ObjectMapResult(pins, MAX_RESULTS, rows.size() > MAX_RESULTS);
    }
}
