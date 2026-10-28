package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ObjectTaskSetCommandCompatibilityTest {
    @Test
    void legacyQueuedCommandWithoutChoicesRetainsItsSerializedFormat() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var command = new ObjectStatusCommand("IN_PROGRESS", 0, null, null, null, null, null, false, null);
        String legacy =
                "{\"status\":\"IN_PROGRESS\",\"version\":0,\"refusalReasonId\":null,\"contractSignedDate\":null,\"contractTypeId\":null,\"openingDate\":null,\"openingPlannedDate\":null,\"plannedDateProvided\":false,\"confirmationToken\":null}";
        // T5770 added signing without JsonInclude, so new serialization carries an explicit null;
        // the legacy form without it must still be readable
        String serializedWithEmptySigning = legacy.replace("}", ",\"signing\":null}");
        assertThat(json.writeValueAsString(command)).isEqualTo(serializedWithEmptySigning);
        var restored = json.readValue(
                "{\"transition\":" + legacy + ",\"approvalRequestId\":null,\"confirmationToken\":null}",
                ObjectTaskSetCommand.class);
        assertThat(restored.transition()).isEqualTo(command);
        assertThat(restored.assignees()).isEmpty();
        assertThat(restored.transition().assignees()).isEmpty();
        assertThat(json.valueToTree(restored).has("assignees")).isFalse();
    }
}
