package dev.workflowcrm.files.infrastructure;

import dev.workflowcrm.files.domain.FileCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface FileCategoryRepository extends Repository<FileCategory, UUID> {
    Optional<FileCategory> findByCode(String code);

    List<FileCategory> findByActiveTrueOrderByName();
}
