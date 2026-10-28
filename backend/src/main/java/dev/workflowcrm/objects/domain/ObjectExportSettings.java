package dev.workflowcrm.objects.domain;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.objects.export")
public record ObjectExportSettings(
        @DefaultValue("workflow-exports") @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String bucket,
        @DefaultValue("24h") @NotNull Duration retention,
        @DefaultValue("5m") @NotNull Duration linkTtl,
        @DefaultValue("268435456") @Min(1048576) @Max(1073741824) long maxFileBytes) {
    public ObjectExportSettings {
        if (retention == null
                || retention.compareTo(Duration.ofMinutes(1)) < 0
                || retention.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("Export retention must be between one minute and seven days");
        if (linkTtl == null
                || linkTtl.compareTo(Duration.ofSeconds(1)) < 0
                || linkTtl.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("Export link lifetime must be between one second and fifteen minutes");
    }
}
