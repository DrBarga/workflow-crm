package dev.workflowcrm.objects.infrastructure;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.objects.duplicates")
record ObjectDuplicateProperties(
        @DecimalMin("1") @DecimalMax("10000") double radiusMetres,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false)
                double nameSimilarity) {}
