package dev.workflowcrm.users.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.shared.read.ReadPage;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserLookupService {
    private final CurrentUser current;
    private final UserManagementStore users;
    private final UserRegionCatalog regions;

    UserLookupService(CurrentUser current, UserManagementStore users, UserRegionCatalog regions) {
        this.current = current;
        this.users = users;
        this.regions = regions;
    }

    public record Manager(UUID id, String fullName, RoleCode role, List<UUID> regionIds) {}

    User actor() {
        return users.find(current.id())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
    }

    public List<UserRegionCatalog.Option> regions() {
        var actor = actor();
        boolean network = !Set.of(RoleCode.MANAGER, RoleCode.REGIONAL_MANAGER)
                .contains(actor.getRole().getCode());
        return regions.regions(actor.getRegionIds(), network);
    }

    public boolean administrator() {
        return actor().getRole().getCode() == RoleCode.SUPERADMIN;
    }

    public Page<Manager> managers(RoleCode role, UUID regionId, UserStatus status, int page, int size) {
        var actor = actor();
        var code = actor.getRole().getCode();
        if (!Set.of(RoleCode.REGIONAL_MANAGER, RoleCode.DEVELOPMENT_DIRECTOR, RoleCode.SUPERADMIN)
                        .contains(code)
                || role != RoleCode.MANAGER
                || (status != null && status != UserStatus.ACTIVE))
            throw new AccessDeniedException("Only active manager selection is available");
        new ReadPage(page, size);
        if (regionId == null) throw new UnprocessableInputException("regionId", "Choose an object region");
        if (code == RoleCode.REGIONAL_MANAGER && !actor.getRegionIds().contains(regionId))
            throw new AccessDeniedException("Region is outside your assignments");
        if (regions.regions(List.of(regionId), false).isEmpty())
            throw new UnprocessableInputException("regionId", "Region does not exist");
        return users.search(RoleCode.MANAGER, regionId, UserStatus.ACTIVE, page, size)
                .map(user -> new Manager(user.getId(), user.getFullName(), RoleCode.MANAGER, List.of(regionId)));
    }
}
