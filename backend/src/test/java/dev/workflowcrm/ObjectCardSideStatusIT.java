package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCardSideStatusIT extends ObjectCardTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "REFUSED"})
    void sideStatusKeepsGeneralAddressEditingRights(String side) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, side);
        String column = side.equals("PAUSED") ? "status_before_pause_id" : "status_before_refusal_id";
        for (String previous : List.of("FOUND", "IC_PREPARATION")) {
            jdbc.update(
                    "update objects set " + column + "=(select id from object_statuses where code=?) where id=?",
                    previous,
                    object);
            assertThat(card(manager, object)
                            .path("editableFields")
                            .path("address")
                            .asBoolean())
                    .isTrue();
        }
    }
}
