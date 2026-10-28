package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.objects.domain.ObjectRegistration;
import dev.workflowcrm.objects.domain.RealEstateObject;
import org.junit.jupiter.api.Test;

class ObjectSchemaMigrationIT extends ObjectSchemaTestSupport {
    @Test
    void createsAllObjectTablesAndUsesNumericCoordinatesWithoutPostgis() {
        assertThat(jdbc.queryForList(
                        "select table_name from information_schema.tables where table_schema = 'public'", String.class))
                .contains(
                        "objects",
                        "object_statuses",
                        "object_status_transitions",
                        "object_types",
                        "refusal_reasons",
                        "file_categories",
                        "object_history",
                        "object_transition_roles",
                        "object_transition_required_fields");
        assertThat(jdbc.queryForList(
                        "select data_type from information_schema.columns where table_name = 'objects' "
                                + "and column_name in ('entrance_longitude', 'entrance_latitude')",
                        String.class))
                .containsExactly("numeric", "numeric");
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'postgis'", Integer.class))
                .isZero();
    }

    @Test
    void seedsNineStatusesAndFinalOpenedStatus() {
        assertThat(jdbc.queryForList("select code from object_statuses order by sort_order", String.class))
                .containsExactly(
                        "FOUND",
                        "IN_PROGRESS",
                        "IC_PREPARATION",
                        "COMMERCIAL_TERMS_APPROVAL",
                        "CONTRACT_APPROVAL",
                        "CONTRACT_SIGNED",
                        "OPENED",
                        "PAUSED",
                        "REFUSED");
        var opened = statuses.findByCode("OPENED").orElseThrow();
        assertThat(opened.isFinal()).isTrue();
        assertThat(opened.isActive()).isTrue();
        assertThat(statuses.findByCode("CONTRACT_SIGNED").orElseThrow().isFinal())
                .isFalse();
        assertThat(statuses.findByCode("REFUSED").orElseThrow().isFinal()).isFalse();
    }

    @Test
    void acceptsOnlyClientRequiredFieldsAndAssignedOwnershipOnCreation() {
        var complete = newObject();
        var minimal = new RealEstateObject(
                new ObjectRegistration(
                        null,
                        null,
                        null,
                        complete.getRegionId(),
                        complete.getManagerId(),
                        complete.getAuthorId(),
                        complete.getEntrance(),
                        complete.getAreas(),
                        complete.getContact()),
                statuses.findByCode("FOUND").orElseThrow());
        objects.saveAndFlush(minimal);
        assertThat(minimal.getId()).isNotNull();
        assertThat(minimal.getName()).isNull();
        assertThat(minimal.getType()).isNull();
        assertThat(minimal.getAddress()).isNull();
    }
}
