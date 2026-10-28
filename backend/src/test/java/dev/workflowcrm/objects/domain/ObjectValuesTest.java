package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ObjectValuesTest {
    @Test
    void acceptsWgs84BoundaryAndKeepsLongitudeBeforeLatitude() {
        var coordinates = new EntranceCoordinates(new BigDecimal("180"), new BigDecimal("-90"));
        assertThat(coordinates.getLongitude()).isEqualByComparingTo("180");
        assertThat(coordinates.getLatitude()).isEqualByComparingTo("-90");
    }

    @Test
    void rejectsCoordinatesOutsideRangeAndUnsupportedPrecision() {
        assertThatThrownBy(() -> new EntranceCoordinates(new BigDecimal("180.1"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntranceCoordinates(BigDecimal.ZERO, new BigDecimal("90.1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntranceCoordinates(new BigDecimal("30.12345678"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsEitherAreaAndRejectsMissingOrNonPositiveAreas() {
        assertThat(new PropertyAreas(null, BigDecimal.ONE).getLandArea()).isEqualByComparingTo("1");
        assertThat(new PropertyAreas(BigDecimal.ONE, null).getObjectArea()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> new PropertyAreas(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropertyAreas(BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropertyAreas(new BigDecimal("0.001"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresContactNameRoleAndAContactChannel() {
        assertThatThrownBy(() -> new ObjectContact(" ", "Owner", "123", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectContact("Owner", " ", "123", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectContact("Owner", "Broker", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        var contact = new ObjectContact(" Owner ", " Broker ", null, " owner@example.com ");
        assertThat(contact.getEmail()).isEqualTo("owner@example.com");
        assertThat(contact.getPhone()).isNull();
    }
}
