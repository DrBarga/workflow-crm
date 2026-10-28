package dev.workflowcrm.integrations.infrastructure;

/** Values of EXTERNAL_INTEGRATIONS_MODE, the switch between real external services and their stubs. */
final class ExternalIntegrationsMode {
    static final String PROPERTY = "app.integrations.mode";

    /** Real external services. Production only. */
    static final String LIVE = "live";

    /** Stubs for dev and stage: test data never leaves for third-party services. */
    static final String MOCK = "mock";

    private ExternalIntegrationsMode() {}
}
