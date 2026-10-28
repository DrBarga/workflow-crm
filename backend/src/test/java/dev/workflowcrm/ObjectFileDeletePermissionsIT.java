package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectFileDeletePermissionsIT extends ObjectFileTestSupport {
    @ParameterizedTest
    @CsvSource({
        "MANAGER,OTHER,true,true",
        "MANAGER,CONTRACTS,true,true",
        "REGIONAL_MANAGER,OTHER,true,true",
        "REGIONAL_MANAGER,CONTRACTS,true,true",
        "CONTRACT_MANAGER,OTHER,true,false",
        "CONTRACT_MANAGER,CONTRACTS,true,false",
        "SUPERADMIN,OTHER,true,true",
        "SUPERADMIN,CONTRACTS,false,false",
        "DEVELOPMENT_DIRECTOR,OTHER,false,false",
        "DEVELOPMENT_DIRECTOR,CONTRACTS,false,false",
        "CEO,OTHER,false,false",
        "CEO,CONTRACTS,false,false"
    })
    void deletionAndMetadataFollowCurrentRoleCategoryAndAuthorship(
            String role, String category, boolean ownAllowed, boolean otherAllowed) throws Exception {
        UUID object = add("Delete permissions", manager, region);
        UUID actor =
                switch (role) {
                    case "MANAGER" -> manager;
                    case "REGIONAL_MANAGER" -> lead;
                    case "CONTRACT_MANAGER" -> contract;
                    case "DEVELOPMENT_DIRECTOR" -> director;
                    case "CEO" -> ceo;
                    default -> admin;
                };
        var own = uploaded(contract, object, category);
        var other = uploaded(manager, object, category);
        jdbc.update("update files set author_id=? where id=?", actor, fileId(own));
        jdbc.update("update files set author_id=? where id=?", colleague, fileId(other));
        var items = filePage(actor, object, Map.of()).path("items");
        if (role.equals("SUPERADMIN") && category.equals("CONTRACTS")) {
            assertThat(items.size()).isZero();
        } else {
            assertThat(items.size()).isEqualTo(2);
            for (JsonNode item : items) {
                boolean expected = fileId(item).equals(fileId(own)) ? ownAllowed : otherAllowed;
                assertThat(item.path("canDelete").asBoolean()).isEqualTo(expected);
            }
        }
        verifyDelete(actor, object, fileId(own), ownAllowed);
        verifyDelete(actor, object, fileId(other), otherAllowed);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name='FILE_DELETED'",
                        Integer.class,
                        object))
                .isEqualTo((ownAllowed ? 1 : 0) + (otherAllowed ? 1 : 0));
    }

    @Test
    void administratorCannotDeleteOwnContractAfterRoleChangeEvenWhenCategoryIsInactive() throws Exception {
        UUID object = add("Former contract manager", manager, region);
        var file = uploaded(contract, object, "CONTRACTS");
        var cookie = cookie(contract);
        jdbc.update("update users set role_id=(select id from roles where code='SUPERADMIN') where id=?", contract);
        jdbc.update("update file_categories set is_active=false where code='CONTRACTS'");
        String before = snapshot(fileId(file));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                    "/api/objects/" + object + "/files/" + fileId(file))
                            .cookie(cookie, csrf)
                            .header("X-XSRF-TOKEN", csrfToken))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("update file_categories set is_active=true where code='CONTRACTS'");
        }
        assertThat(snapshot(fileId(file))).isEqualTo(before);
        assertThat(count("object_history")).isEqualTo(1);
    }

    private void verifyDelete(UUID actor, UUID object, UUID file, boolean allowed) throws Exception {
        String before = snapshot(file);
        deleteFile(actor, object, file).andExpect(allowed ? status().isNoContent() : status().isForbidden());
        if (allowed) {
            assertThat(jdbc.queryForObject("select deleted_at is not null from files where id=?", Boolean.class, file))
                    .isTrue();
        } else {
            assertThat(snapshot(file)).isEqualTo(before);
        }
    }

    private String snapshot(UUID file) {
        return jdbc.queryForObject("select to_jsonb(f)::text from files f where id=?", String.class, file);
    }
}
