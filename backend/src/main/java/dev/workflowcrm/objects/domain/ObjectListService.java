package dev.workflowcrm.objects.domain;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectListService {
    private final ObjectListAccess access;
    private final ObjectListStore store;
    private final ObjectListCursors cursors;
    private final Clock clock;

    public ObjectListService(ObjectListAccess access, ObjectListStore store, ObjectListCursors cursors, Clock clock) {
        this.access = access;
        this.store = store;
        this.cursors = cursors;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectListPage list(UUID actor, ObjectListFilter filter, String cursor) {
        var scope = access.require(actor);
        var position = cursor == null
                ? new ObjectListPosition(
                        null, null, clock.instant(), clock.instant().plus(Duration.ofMinutes(15)))
                : cursors.read(cursor, filter, scope);
        if (scope.regionRestricted() && scope.regionIds().isEmpty()) return new ObjectListPage(List.of(), null, false);
        var rows = store.find(filter, scope, position);
        boolean more = rows.size() > filter.limit();
        var page = rows.subList(0, Math.min(filter.limit(), rows.size()));
        var full = store.full(page.stream()
                .filter(r -> scope.full(r.managerId(), r.regionId()))
                .map(ObjectListStore.Row::id)
                .toList());
        List<ObjectListItem> content = page.stream()
                .map(r -> scope.full(r.managerId(), r.regionId()) ? (ObjectListItem) full.get(r.id()) : r.restricted())
                .toList();
        var last = more ? page.getLast() : null;
        String next = last == null
                ? null
                : cursors.write(
                        new ObjectListPosition(last.id(), last.value(), position.snapshot(), position.expiresAt()),
                        filter,
                        scope);
        return new ObjectListPage(content, next, more);
    }
}
