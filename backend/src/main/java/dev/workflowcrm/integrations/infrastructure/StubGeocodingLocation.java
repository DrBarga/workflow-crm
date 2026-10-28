package dev.workflowcrm.integrations.infrastructure;

/** Fixed answer of the stub geocoder used when EXTERNAL_INTEGRATIONS_MODE=mock. */
final class StubGeocodingLocation {
    /** Testers must see at a glance that the address did not come from a real geocoder. */
    static final String ADDRESS_PREFIX = "Тестова адреса";

    /** Kyiv: imported objects without a region still match a CRM region when one with this code exists. */
    static final String REGION_CODE = "UA-30";

    static final String REGION_NAME = "Київ";

    private StubGeocodingLocation() {}
}
