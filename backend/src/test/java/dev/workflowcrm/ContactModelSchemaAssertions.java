package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class ContactModelSchemaAssertions {
    private ContactModelSchemaAssertions() {}

    static void verify(JdbcTemplate jdbc, UUID contact, UUID viewer, UUID second) {
        assertThatThrownBy(() ->
                        jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", second, contact))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() ->
                        jdbc.update("update contacts set contact_type_id=? where id=?", UUID.randomUUID(), contact))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update contacts set author_id=? where id=?", UUID.randomUUID(), contact))
                .isInstanceOf(DataAccessException.class);
        jdbc.update(
                "insert into contact_phone_views(contact_id,viewer_id) values (?,?),(?,?)",
                contact,
                viewer,
                contact,
                viewer);
        assertThat(jdbc.queryForObject("select count(*) from contact_phone_views", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into contact_phone_views(contact_id,viewer_id) values (?,?)",
                        UUID.randomUUID(),
                        viewer))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from contacts where id=?", contact))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into contact_phone_views(contact_id,viewer_id) values (?,?)",
                        contact,
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        var indexes = jdbc.queryForList(
                "select indexname from pg_indexes where tablename in ('contacts','contact_phone_views')", String.class);
        assertThat(indexes)
                .contains(
                        "ix_contacts_normalized_phone",
                        "ix_contacts_name",
                        "ix_contact_phone_views_contact",
                        "ix_contact_phone_views_viewer");
        jdbc.execute("set enable_seqscan=off");
        try {
            assertThat(jdbc.queryForList(
                            "explain select id from contacts where normalized_phone like '380%'", String.class))
                    .anyMatch(line -> line.contains("ix_contacts_normalized_phone"));
            assertThat(jdbc.queryForList(
                            "explain select id from contacts where lower(name) like 'влас%'", String.class))
                    .anyMatch(line -> line.contains("ix_contacts_name"));
        } finally {
            jdbc.execute("reset enable_seqscan");
        }
    }
}
