package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository
        extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(UUID id);

    @Override
    @EntityGraph(attributePaths = {"role"})
    org.springframework.data.domain.Page<User> findAll(
            org.springframework.data.jpa.domain.Specification<User> specification,
            org.springframework.data.domain.Pageable pageable);

    boolean existsByTelegramChatIdAndIdNot(Long telegramChatId, UUID id);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(String email);
}
