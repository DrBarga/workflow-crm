package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectExportAssignedRegionsIT extends ObjectExportTestSupport {
    @Test
    void additionalRegionsAreExportedButForeignListPinsAreExcluded() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", lead, otherRegion);
        add("Primary", manager, region);
        add("Additional", outsider, otherRegion);
        var third = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values (?,'EXPORT-THIRD','Third')", third);
        add("Foreign", outsider, third);
        assertThat(names(page(lead, Map.of()))).contains("Foreign");
        assertThat(csv(lead, Map.of())).contains("Primary", "Additional").doesNotContain("Foreign");
        assertThat(csv(lead, Map.of("regionId", third.toString())).lines().count())
                .isEqualTo(1);
    }

    @Test
    void revokingAnAdditionalRegionRevokesExportDownloads() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", lead, otherRegion);
        UUID id = ready(lead, "CSV");
        jdbc.update("delete from user_regions where user_id=? and region_id=?", lead, otherRegion);
        exportDownload(lead, id).andExpect(status().isForbidden());
    }
}
