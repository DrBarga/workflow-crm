package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.objects.domain.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectFieldPersistenceIT extends IcDecisionTestSupport {
    @Autowired
    EntityManager entities;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void persistsTypedBlocksContractReferenceAndAllSevenDatesAndIncrementsVersion() {
        var d = LocalDate.of(2026, 10, 5);
        var technical = new TechnicalSpecifications(
                n("700"),
                n("400"),
                n("200"),
                n("100"),
                n("300"),
                n("4.5"),
                n("120"),
                n("10.5"),
                PowerConnectionCategory.II,
                SewageType.LOCAL_TREATMENT);
        var commercial = new CommercialTerms(
                "Оренда", n("25000"), n("7"), n("2.5"), CounterpartyTaxStatus.COMPANY_VAT, 90, n("10"));
        var dates = new ObjectMilestones(
                d, d.plusDays(1), d.plusDays(2), d.plusDays(3), d.plusDays(4), d.plusDays(5), d.plusDays(6));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var value = entities.find(RealEstateObject.class, object);
            var type = entities.createQuery("select t from ContractType t where t.code='PRIMARY'", ContractType.class)
                    .getSingleResult();
            value.specify(technical, commercial, type);
            value.schedule(dates);
            entities.flush();
            assertThat(value.getVersion()).isEqualTo(1);
            entities.clear();
            var loaded = entities.find(RealEstateObject.class, object);
            assertThat(loaded.getSpecifications().getTechnical())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(technical);
            assertThat(loaded.getSpecifications().getCommercial())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(commercial);
            assertThat(loaded.getSpecifications().getContractTypeReference().getName())
                    .isEqualTo("Основний");
            assertThat(loaded.getMilestones()).isEqualTo(dates.calculated(90));
            assertThat(loaded.getGoogleMapsUrl()).endsWith("50.4501000%2C30.5234000");
        });
    }

    @Test
    void enforcesNumericEnumsTextLimitsAndContractReferenceInPostgres() {
        for (String update : java.util.List.of(
                "building_area=-1",
                "fixed_rent_amount=-1",
                "construction_term_days=-1",
                "turnover_payment_percent=100.01",
                "power_connection_category='IV'",
                "sewage_type='UNKNOWN'",
                "counterparty_tax_status='UNKNOWN'",
                "contract_type_id='00000000-0000-0000-0000-000000000001'",
                "investment_committee_conditions=repeat('а',51)",
                "investment_committee_refusal_comment=repeat('а',51)"))
            assertThatThrownBy(() -> jdbc.update("update objects set " + update + " where id=?", object))
                    .as(update)
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void preservesActualOpeningAndSigningDatesWhenAddingPlannedDates() {
        jdbc.update(
                "update objects set contract_signed_date='2026-09-01',opening_date='2026-09-30',opening_planned_date='2026-09-20',construction_acceptance_planned_date='2026-09-10',construction_term_days=10 where id=?",
                object);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var value = entities.find(RealEstateObject.class, object);
            assertThat(value.getContractSignedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(value.getOpeningDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(value.getMilestones().openingPlannedDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        });
    }

    private BigDecimal n(String value) {
        return new BigDecimal(value);
    }
}
