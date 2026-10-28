package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.ObjectFileContext.Access;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

final class ObjectFilePolicy {
    private ObjectFilePolicy() {}

    static boolean restrictedVisible(Access access) {
        return access.fullView()
                        && Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO")
                                .contains(access.role())
                || "CONTRACT_MANAGER".equals(access.role());
    }

    static void requireView(Access access) {
        if (!access.fullView() && !"CONTRACT_MANAGER".equals(access.role())) deny();
    }

    static void requireUpload(Access access) {
        requireView(access);
        if (!Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER")
                .contains(access.role())) deny();
    }

    static boolean canDelete(Access access, UUID actor, UUID author, boolean restricted) {
        return switch (access.role()) {
            case "MANAGER" -> access.responsibleManager() || actor.equals(author);
            case "REGIONAL_MANAGER" -> access.fullView();
            case "CONTRACT_MANAGER" -> actor.equals(author);
            case "SUPERADMIN" -> !restricted;
            default -> false;
        };
    }

    static void requireDelete(Access access, UUID actor, UUID author, boolean restricted) {
        if (!canDelete(access, actor, author, restricted)) deny();
    }

    static void requireCategory(Access access, boolean restricted) {
        if (restricted && !restrictedVisible(access)) deny();
    }

    private static void deny() {
        throw new AccessDeniedException("File access denied");
    }
}
