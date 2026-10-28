package dev.workflowcrm.files.domain;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FilePropertiesTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(FileConfiguration.class);

    @Test
    void defaultsAllowAllRequiredFormatsWithTwentyMiBLimit() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            var properties = application.getBean(FileProperties.class);
            assertThat(properties.allowedExtensions())
                    .containsExactlyInAnyOrder("jpg", "png", "heic", "pdf", "docx", "xlsx", "dwg");
            assertThat(properties.maxSizeBytes()).isEqualTo(20971520);
        });
    }

    @Test
    void configuredFormatsReplaceDefaultsAndConfiguredLimitIsEnforced() {
        context.withPropertyValues("app.files.allowed-extensions=pdf", "app.files.max-size-bytes=8")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var properties = application.getBean(FileProperties.class);
                    assertThat(properties.allowedExtensions()).containsExactly("pdf");
                    var validation = new FileUploadValidation(properties);
                    assertThatThrownBy(() -> validation.validate("photo.heic", new ByteArrayInputStream(new byte[20])))
                            .isInstanceOf(UnprocessableInputException.class)
                            .satisfies(error -> assertThat(((UnprocessableInputException) error)
                                            .violations()
                                            .getFirst()
                                            .message())
                                    .isEqualTo("Allowed file formats: pdf"));
                    assertThatThrownBy(() -> validation.validate(
                                    "document.pdf",
                                    new ByteArrayInputStream(
                                            "%PDF-1.77".getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
                            .isInstanceOf(UnprocessableInputException.class)
                            .satisfies(error -> assertThat(((UnprocessableInputException) error)
                                            .violations()
                                            .getFirst()
                                            .message())
                                    .contains("8 bytes"));
                });
    }
}
