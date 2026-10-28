package dev.workflowcrm.files.domain;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileUploadValidationTest {
    private final FileProperties properties = new FileProperties(
            "test-files",
            "test-task-files",
            20971520,
            Set.of("jpg", "png", "heic", "pdf", "docx", "xlsx", "dwg"),
            Duration.ofMinutes(5));
    private final FileUploadValidation validation = new FileUploadValidation(properties);

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "png", "heic", "pdf", "docx", "xlsx", "dwg"})
    void supportsAllConfiguredFormatsAndDerivesContentType(String extension) throws Exception {
        byte[] content =
                switch (extension) {
                    case "jpg" -> new byte[] {(byte) 255, (byte) 216, (byte) 255, 0};
                    case "png" -> new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
                    case "pdf" -> "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
                    case "dwg" -> "AC1032".getBytes(StandardCharsets.US_ASCII);
                    case "heic" ->
                        new byte[] {0, 0, 0, 20, 102, 116, 121, 112, 104, 101, 105, 99, 0, 0, 0, 0, 109, 105, 102, 49};
                    default -> office(extension);
                };
        var result = validation.validate("Документ." + extension.toUpperCase(), new ByteArrayInputStream(content));
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.contentType()).isNotBlank().isNotEqualTo("application/octet-stream");
    }

    @Test
    void rejectsRenamedOfficeArchiveAndMalformedImage() throws Exception {
        assertThatThrownBy(() -> validation.validate("x.xlsx", new ByteArrayInputStream(office("docx"))))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() ->
                        validation.validate("x.png", new ByteArrayInputStream(new byte[] {(byte) 137, 80, 78, 71})))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> validation.validate("x.heic", new ByteArrayInputStream(new byte[] {
                    0, 0, 0, 16, 102, 116, 121, 112, 97, 118, 105, 102, 0, 0, 0, 0
                })))
                .isInstanceOf(UnprocessableInputException.class);
    }

    @Test
    void configuredLimitAndFormatRestrictionAreEnforcedWithoutReadingAnUnboundedStream() throws Exception {
        var limited = new FileUploadValidation(
                new FileProperties("test-files", "test-task-files", 8, Set.of("pdf"), Duration.ofSeconds(1)));
        assertThat(limited.validate("x.pdf", new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                        .content())
                .hasSize(8);
        assertThatThrownBy(() -> limited.validate(
                        "x.pdf", new ByteArrayInputStream("%PDF-1.77".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(UnprocessableInputException.class)
                .satisfies(error -> assertThat(((UnprocessableInputException) error)
                                .violations()
                                .getFirst()
                                .message())
                        .contains("8 bytes"));
        assertThatThrownBy(() -> limited.validate("x.jpg", new ByteArrayInputStream(new byte[8])))
                .isInstanceOf(UnprocessableInputException.class);
    }

    @Test
    void rejectsInvalidUnicodeAndHeaderInjectionInNames() {
        for (String name : java.util.List.of("bad\uD800.pdf", "x\u0000.pdf", "../x.pdf", "C:\\x.pdf", "x\n.pdf"))
            assertThatThrownBy(() -> validation.validate(name, new ByteArrayInputStream(new byte[0])))
                    .isInstanceOf(UnprocessableInputException.class);
    }

    static byte[] office(String extension) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (String name : java.util.List.of(
                    "[Content_Types].xml", extension.equals("docx") ? "word/document.xml" : "xl/workbook.xml")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
