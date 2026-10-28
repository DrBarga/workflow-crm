package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectFileRegionDeleteIT extends ObjectFileTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"OTHER", "CONTRACTS"})
    void managerCanDeleteOtherAuthorsFilesOnOwnObjectInSecondAssignedRegion(String category) throws Exception {
        assign(manager, otherRegion);
        UUID object = add("Second manager region", manager, otherRegion);
        var file = uploaded(director, object, category);
        assertThat(filePage(manager, object, Map.of())
                        .path("items")
                        .get(0)
                        .path("canDelete")
                        .asBoolean())
                .isTrue();
        deleteFile(manager, object, fileId(file)).andExpect(status().isNoContent());
    }

    @Test
    void regionalManagerDeletesWithinAllAssignmentsAndLosesPermissionWhenAssignmentIsRemoved() throws Exception {
        assign(lead, otherRegion);
        UUID object = add("Second assigned region", outsider, otherRegion);
        var contractFile = uploaded(director, object, "CONTRACTS");
        var ownFile = uploaded(lead, object, "OTHER");
        for (var file : filePage(lead, object, Map.of()).path("items")) {
            assertThat(file.path("canDelete").asBoolean()).isTrue();
        }
        deleteFile(lead, object, fileId(contractFile)).andExpect(status().isNoContent());
        jdbc.update("delete from user_regions where user_id=? and region_id=?", lead, otherRegion);
        deleteFile(lead, object, fileId(ownFile)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
                        "select deleted_at is null from files where id=?", Boolean.class, fileId(ownFile)))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name='FILE_DELETED'",
                        Integer.class,
                        object))
                .isEqualTo(1);
    }

    private void assign(UUID user, UUID region) {
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", user, region);
    }
}
