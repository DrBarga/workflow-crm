package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.workflowcrm.objects.domain.ObjectHistory;
import dev.workflowcrm.objects.domain.RealEstateObject;
import jakarta.persistence.RollbackException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ObjectSchemaChecks4IT extends ObjectSchemaTestSupport {
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsConcurrentUpdateWithStaleVersion() {
        UUID objectId;
        try (var creator = entityManagerFactory.createEntityManager()) {
            creator.getTransaction().begin();
            var object = newObject();
            creator.persist(object);
            creator.getTransaction().commit();
            objectId = object.getId();
        }
        try (var first = entityManagerFactory.createEntityManager();
                var second = entityManagerFactory.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var firstCopy = first.find(RealEstateObject.class, objectId);
            var secondCopy = second.find(RealEstateObject.class, objectId);
            firstCopy.rename("First update");
            first.getTransaction().commit();
            secondCopy.rename("Stale update");
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(RollbackException.class);
            assertThat(jdbc.queryForObject("select name from objects where id = ?", String.class, objectId))
                    .isEqualTo("First update");
        } finally {
            ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id = ?", objectId));
        }
    }

    @Test
    void editsPreviouslyEmptySpecificationsAfterReloadWithoutChangingOtherFields() {
        var object = objects.saveAndFlush(newObject());
        entityManager.clear();
        var persisted = objects.findById(object.getId()).orElseThrow();
        persisted.describeCommercialTerms("Lease terms", "Lease");
        persisted.describeTechnicalSpecifications("Survey required");
        objects.flush();
        entityManager.clear();
        var reloaded = objects.findById(object.getId()).orElseThrow();
        assertThat(reloaded.getSpecifications().getCommercialTerms()).isEqualTo("Lease terms");
        assertThat(reloaded.getSpecifications().getTechnicalSpecifications()).isEqualTo("Survey required");
        assertThat(reloaded.getStatus().getCode()).isEqualTo("FOUND");
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void storesUserAndSystemHistoryWithObjectVersionAndAuthor() {
        var object = objects.saveAndFlush(newObject());
        history.save(new ObjectHistory(
                object.getId(),
                object.getAuthorId(),
                ObjectHistory.ActorType.USER,
                "name",
                null,
                object.getName(),
                object.getVersion()));
        history.save(new ObjectHistory(
                object.getId(),
                null,
                ObjectHistory.ActorType.SYSTEM,
                "status",
                "IC_PREPARATION",
                "COMMERCIAL_TERMS_APPROVAL",
                object.getVersion()));
        entityManager.flush();
        entityManager.clear();
        assertThat(history.findByObjectIdOrderByCreatedAtAsc(object.getId())).hasSize(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id = ? " + "and object_version = ?",
                        Integer.class,
                        object.getId(),
                        object.getVersion()))
                .isEqualTo(2);
    }

    @Test
    void rejectsCoordinatesOutsideWgs84AtDatabaseLevel() {
        var object = objects.saveAndFlush(newObject());
        assertThatThrownBy(() -> jdbc.update("update objects set entrance_latitude = 91 where id = ?", object.getId()))
                .isInstanceOf(DataAccessException.class);
    }
}
