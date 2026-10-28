package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.FileOwner;
import dev.workflowcrm.files.api.FileStorage;
import dev.workflowcrm.files.api.ObjectFile;
import dev.workflowcrm.files.api.ObjectFileContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectFileService {
    private final ObjectFileContext objects;
    private final ObjectFileStore files;
    private final FileStorage storage;
    private final ObjectFileWriter writer;

    ObjectFileService(ObjectFileContext objects, ObjectFileStore files, FileStorage storage, ObjectFileWriter writer) {
        this.objects = objects;
        this.files = files;
        this.storage = storage;
        this.writer = writer;
    }

    @Transactional(readOnly = true)
    public void requireUpload(UUID actor, UUID object) {
        ObjectFilePolicy.requireUpload(objects.read(actor, object));
    }

    @Transactional(readOnly = true)
    public void requireCategory(UUID actor, UUID object, UUID category) {
        var access = objects.read(actor, object);
        ObjectFilePolicy.requireUpload(access);
        ObjectFilePolicy.requireCategory(access, files.category(category, false).restricted());
    }

    public ObjectFile upload(UUID actor, UUID object, UUID category, String name, InputStream content)
            throws IOException {
        requireCategory(actor, object, category);
        return writer.upload(actor, object, category, name, content);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectFilePage list(UUID actor, UUID object, String cursor, int limit) {
        var access = objects.read(actor, object);
        ObjectFilePolicy.requireView(access);
        if (limit < 1 || limit > 100)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "File limit must be between 1 and 100");
        var position = cursor == null
                ? new ObjectFileCursor.Position(0, files.snapshot(object))
                : ObjectFileCursor.decode(object, cursor);
        var rows = files.page(
                object, ObjectFilePolicy.restrictedVisible(access), position.after(), position.snapshot(), limit + 1);
        boolean more = rows.size() > limit;
        var visible = rows.stream().limit(limit).toList();
        return new ObjectFilePage(
                visible.stream()
                        .map(row -> ObjectFileWriter.view(row, access, actor))
                        .toList(),
                more ? ObjectFileCursor.encode(object, visible.getLast().sequence(), position.snapshot()) : null,
                more);
    }

    @Transactional(readOnly = true)
    public ObjectFileLink link(UUID actor, UUID object, UUID id) {
        var access = objects.read(actor, object);
        ObjectFilePolicy.requireView(access);
        var file = files.get(object, id);
        ObjectFilePolicy.requireCategory(access, file.restricted());
        var download = storage.download(FileOwner.object(object), id);
        return new ObjectFileLink(download.url(), download.expiresAt());
    }

    public void delete(UUID actor, UUID object, UUID id) {
        writer.delete(actor, object, id);
    }
}
