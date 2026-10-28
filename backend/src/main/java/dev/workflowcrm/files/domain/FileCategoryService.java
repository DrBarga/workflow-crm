package dev.workflowcrm.files.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileCategoryService {
    private final ObjectAssignmentQuery users;
    private final FileCategoryCatalog categories;

    FileCategoryService(ObjectAssignmentQuery users, FileCategoryCatalog categories) {
        this.users = users;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<FileCategoryCatalog.Option> list(UUID actor) {
        var user = users.member(actor)
                .filter(ObjectAssignmentQuery.Member::active)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        return categories.active(!"SUPERADMIN".equals(user.role()));
    }
}
