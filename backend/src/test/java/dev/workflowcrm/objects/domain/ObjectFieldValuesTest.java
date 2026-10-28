package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ObjectFieldValuesTest {
    @Test
    void computesGoogleMapsUrlFromLatitudeAndLongitudeWithEncodedSeparator() {
        var point = new EntranceCoordinates(new BigDecimal("30.5234000"), new BigDecimal("50.4501000"));
        assertThat(point.googleMapsUrl())
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=50.4501000%2C30.5234000");
    }

    @Test
    void numericBoundariesRejectNegativeValuesAndSilentRounding() {
        assertThatThrownBy(() -> ObjectNumbers.nonnegative(new BigDecimal("-0.01"), 14, 2, "area"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectNumbers.nonnegative(new BigDecimal("1.001"), 14, 2, "area"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectNumbers.nonnegative(new BigDecimal("1000000000000"), 14, 2, "area"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ObjectNumbers.nonnegative(new BigDecimal("999999999999.99"), 14, 2, "area"))
                .isNotNull();
        assertThat(ObjectNumbers.nonnegative(new BigDecimal("0.000"), 14, 2, "area"))
                .isEqualByComparingTo("0");
    }

    @Test
    void validatesCommitteeTextUsingCharactersRatherThanUtf16Length() {
        var date = LocalDate.of(2026, 10, 5);
        assertThat(new IcDecisionCommand(
                                InvestmentCommitteeDecision.APPROVED_WITH_CONDITIONS, date, "а".repeat(50), 0, null)
                        .conditions())
                .hasSize(50);
        assertThat(new IcDecisionCommand(InvestmentCommitteeDecision.REFUSED, date, null, "😀".repeat(50), 0, null)
                        .refusalComment())
                .isEqualTo("😀".repeat(50));
        assertThatThrownBy(() -> new IcDecisionCommand(
                        InvestmentCommitteeDecision.APPROVED_WITH_CONDITIONS, date, "а".repeat(51), 0, null))
                .isInstanceOf(dev.workflowcrm.shared.error.UnprocessableInputException.class);
        assertThatThrownBy(() ->
                        new IcDecisionCommand(InvestmentCommitteeDecision.REFUSED, date, null, "а".repeat(51), 0, null))
                .isInstanceOf(dev.workflowcrm.shared.error.UnprocessableInputException.class);
    }

    @Test
    void commercialAndMilestoneBoundariesAreExplicit() {
        assertThatThrownBy(() -> new CommercialTerms(null, null, null, new BigDecimal("100.01"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommercialTerms(null, null, null, null, null, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CommercialTerms(null, null, null, null, null, null, null).rentIndexationPercent())
                .isNull();
        assertThatThrownBy(() -> new ObjectMilestones(null, null, null, null, LocalDate.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(InvestmentCommitteeDecision.APPROVED.getLabel()).isEqualTo("Затверджено");
        assertThat(InvestmentCommitteeDecision.APPROVED_WITH_CONDITIONS.getLabel())
                .isEqualTo("Затверджено з умовою");
    }
}
