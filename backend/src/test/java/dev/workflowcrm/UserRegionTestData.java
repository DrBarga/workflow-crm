package dev.workflowcrm;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class UserRegionTestData {
    private UserRegionTestData() {}

    static void replace(JdbcTemplate jdbc, UUID user, UUID... regions) {
        jdbc.update("delete from user_regions where user_id=?", user);
        assign(jdbc, user, regions);
    }

    static void assign(JdbcTemplate jdbc, UUID user, UUID... regions) {
        for (UUID region : regions)
            if (region != null)
                jdbc.update(
                        "insert into user_regions(user_id,region_id) values (?,?) on conflict do nothing",
                        user,
                        region);
    }
}
