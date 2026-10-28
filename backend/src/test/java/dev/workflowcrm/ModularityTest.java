package dev.workflowcrm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(WorkflowApplication.class);

    @Test
    void modulesRespectDeclaredBoundaries() {
        modules.verify();
    }
}
