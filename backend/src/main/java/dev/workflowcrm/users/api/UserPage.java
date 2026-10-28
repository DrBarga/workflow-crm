package dev.workflowcrm.users.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record UserPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> UserPage<T> of(Page<T> page) {
        return new UserPage<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
