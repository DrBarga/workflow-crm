package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.objects.domain.ObjectDataSource;
import dev.workflowcrm.objects.domain.RealEstateObject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectDataSourceIT extends ObjectStatusTestSupport {
    @Autowired
    EntityManager entities;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void storesOptionalSourceAndCanClearItWithoutChangingObjectDetails() {
        var before = details();
        var transactions = new TransactionTemplate(transactionManager);
        UUID source = jdbc.queryForObject("select id from object_data_sources where code='BROKER'", UUID.class);
        transactions.executeWithoutResult(status -> {
            var site = entities.find(RealEstateObject.class, object);
            assertThat(site.getDataSource()).isNull();
            site.selectDataSource(entities.find(ObjectDataSource.class, source));
        });
        transactions.executeWithoutResult(status -> {
            var site = entities.find(RealEstateObject.class, object);
            assertThat(site.getDataSource().getCode()).isEqualTo("BROKER");
            assertThat(site.getDataSource().getName()).isEqualTo("Брокер");
            assertThat(site.getDataSource().isActive()).isTrue();
            site.selectDataSource(null);
        });
        assertThat(jdbc.queryForObject("select data_source_id from objects where id=?", UUID.class, object))
                .isNull();
        assertThat(details()).isEqualTo(before);
    }

    @Test
    void seedsExactLabelsAndEnforcesReferenceUniquenessAndDeletionProtection() {
        assertThat(jdbc.queryForList("select name from object_data_sources order by code", String.class))
                .containsExactly("Оголошення", "Брокер", "Власник напряму", "Інше", "Власний пошук");
        assertThatThrownBy(() -> jdbc.update("insert into object_data_sources(code,name) values('BROKER','Другий')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into object_data_sources(code,name) values('EMPTY','  ')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () -> jdbc.update("update objects set data_source_id=? where id=?", UUID.randomUUID(), object))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "update objects set data_source_id=(select id from object_data_sources where code='BROKER') where id=?",
                object);
        assertThatThrownBy(() -> jdbc.update("delete from object_data_sources where code='BROKER'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String details() {
        return jdbc.queryForObject(
                "select (to_jsonb(o)-'data_source_id'-'version'-'updated_at')::text from objects o where id=?",
                String.class,
                object);
    }
}
