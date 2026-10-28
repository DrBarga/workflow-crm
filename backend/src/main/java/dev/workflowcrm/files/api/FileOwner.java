package dev.workflowcrm.files.api;

import java.util.Objects;
import java.util.UUID;

public record FileOwner(Type type, UUID id) {
    public enum Type {
        OBJECT,
        TASK
    }

    public FileOwner {
        Objects.requireNonNull(type, "File owner type required");
        Objects.requireNonNull(id, "File owner id required");
    }

    public static FileOwner object(UUID id) {
        return new FileOwner(Type.OBJECT, id);
    }

    public static FileOwner task(UUID id) {
        return new FileOwner(Type.TASK, id);
    }

    public String prefix() {
        return (type == Type.OBJECT ? "objects/" : "tasks/") + id + "/";
    }
}
