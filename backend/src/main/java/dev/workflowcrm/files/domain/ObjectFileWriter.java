package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.FileOwner;
import dev.workflowcrm.files.api.FileStorage;
import dev.workflowcrm.files.api.ObjectFile;
import dev.workflowcrm.files.api.ObjectFileContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectFileWriter {
    private final ObjectFileContext objects;
    private final ObjectFileStore files;
    private final FileStorage storage;

    ObjectFileWriter(ObjectFileContext objects, ObjectFileStore files, FileStorage storage) {
        this.objects = objects;
        this.files = files;
        this.storage = storage;
    }

    @Transactional(rollbackFor = IOException.class)
    public ObjectFile upload(UUID actor, UUID object, UUID category, String name, InputStream content)
            throws IOException {
        var access = objects.lock(actor, object);
        ObjectFilePolicy.requireUpload(access);
        var directory = files.category(category, true);
        ObjectFilePolicy.requireCategory(access, directory.restricted());
        var stored = storage.upload(
                FileOwner.object(object),
                actor,
                category,
                name,
                content,
                file -> objects.uploaded(
                        actor,
                        object,
                        access.version(),
                        files.get(object, file.id()).file()));
        return view(files.get(object, stored.id()), access, actor);
    }

    @Transactional
    public void delete(UUID actor, UUID object, UUID id) {
        var access = objects.lock(actor, object);
        var previous = files.get(object, id);
        ObjectFilePolicy.requireDelete(access, actor, previous.file().author().id(), previous.restricted());
        files.delete(object, id);
        objects.deleted(actor, object, access.version(), previous.file());
    }

    static ObjectFile view(ObjectFileStore.Row row, ObjectFileContext.Access access, UUID actor) {
        var f = row.file();
        return new ObjectFile(
                f.id(),
                f.category(),
                f.author(),
                f.name(),
                f.contentType(),
                f.sizeBytes(),
                f.createdAt(),
                ObjectFilePolicy.canDelete(access, actor, f.author().id(), row.restricted()));
    }
}
