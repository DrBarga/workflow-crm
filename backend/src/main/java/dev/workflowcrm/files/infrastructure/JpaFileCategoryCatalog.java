package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.domain.FileCategoryCatalog;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaFileCategoryCatalog implements FileCategoryCatalog {
    private final FileCategoryRepository categories;

    JpaFileCategoryCatalog(FileCategoryRepository categories) {
        this.categories = categories;
    }

    public List<Option> active(boolean restricted) {
        return categories.findByActiveTrueOrderByName().stream()
                .filter(category -> restricted || !category.isRestricted())
                .map(category ->
                        new Option(category.getId(), category.getCode(), category.getName(), category.isRestricted()))
                .toList();
    }
}
