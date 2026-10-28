package dev.workflowcrm.objects.domain;

import static org.springframework.http.HttpStatus.*;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectExportService {
    private final ObjectExportAccess access;
    private final ObjectExportStore store;
    private final ObjectExportFiles files;
    private final ObjectExportSettings settings;
    private final Clock clock;

    ObjectExportService(
            ObjectExportAccess access,
            ObjectExportStore store,
            ObjectExportFiles files,
            ObjectExportSettings settings,
            Clock clock) {
        this.access = access;
        this.store = store;
        this.files = files;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public ObjectExport.View request(UUID actor, ObjectExport.Format format, ObjectListFilter filter) {
        var scope = access.require(actor);
        var id = UUID.randomUUID();
        var now = clock.instant();
        var job = new ObjectExport.Job(
                id,
                actor,
                format,
                filter,
                scope,
                now,
                now.plus(settings.retention()),
                settings.bucket(),
                "exports/" + actor + "/" + id + "/registry." + format.extension(),
                ObjectExport.State.QUEUED,
                0,
                null,
                0);
        store.enqueue(job);
        return job.view(now);
    }

    @Transactional(readOnly = true)
    public ObjectExport.View status(UUID actor, UUID id) {
        var scope = access.require(actor);
        var job = store.get(id, actor);
        access.unchanged(scope, job.scope());
        return job.view(clock.instant());
    }

    @Transactional(readOnly = true)
    public ObjectExport.Download download(UUID actor, UUID id) {
        var scope = access.require(actor);
        var job = store.get(id, actor);
        access.unchanged(scope, job.scope());
        var now = clock.instant();
        if (!now.isBefore(job.expiresAt()) || job.state() == ObjectExport.State.EXPIRED)
            throw new ResponseStatusException(GONE, "Export expired; request a new export");
        if (job.state() != ObjectExport.State.READY) throw new ResponseStatusException(CONFLICT, "Export is not ready");
        var remaining = Duration.between(now, job.expiresAt());
        var ttl = remaining.compareTo(settings.linkTtl()) < 0 ? remaining : settings.linkTtl();
        ttl = Duration.ofSeconds(ttl.toSeconds());
        if (ttl.isZero()) throw new ResponseStatusException(GONE, "Export expired; request a new export");
        return new ObjectExport.Download(files.link(job, ttl), now.plus(ttl));
    }
}
