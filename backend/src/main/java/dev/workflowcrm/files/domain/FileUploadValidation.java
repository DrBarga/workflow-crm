package dev.workflowcrm.files.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class FileUploadValidation {
    private static final Map<String, String> TYPES = Map.of(
            "jpg",
            "image/jpeg",
            "png",
            "image/png",
            "heic",
            "image/heic",
            "pdf",
            "application/pdf",
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "dwg",
            "image/vnd.dwg");
    private final FileProperties properties;

    public FileUploadValidation(FileProperties properties) {
        this.properties = properties;
    }

    public FileUpload validate(String name, InputStream input) throws IOException {
        if (name == null
                || name.isBlank()
                || !StandardCharsets.UTF_8.newEncoder().canEncode(name)
                || name.codePointCount(0, name.length()) > 255
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\' || c == 0x7f)
                || name.endsWith(".")
                || name.endsWith(" ")) fail("Valid file name required");
        int dot = name.lastIndexOf('.');
        String extension = dot < 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!properties.allowedExtensions().contains(extension))
            fail("Allowed file formats: " + String.join(", ", new java.util.TreeSet<>(properties.allowedExtensions())));
        byte[] bytes = input.readNBytes(Math.toIntExact(properties.maxSizeBytes() + 1));
        if (bytes.length == 0) fail("File must not be empty");
        if (bytes.length > properties.maxSizeBytes()) fail("File limit is " + properties.maxSizeBytes() + " bytes");
        if (!matches(extension, bytes)) fail("File content does not match its format");
        return new FileUpload(name, TYPES.get(extension), bytes);
    }

    private boolean matches(String extension, byte[] bytes) throws IOException {
        return switch (extension) {
            case "jpg" ->
                bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "png" ->
                bytes.length >= 8
                        && java.util.Arrays.equals(
                                java.util.Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "pdf" -> starts(bytes, "%PDF-");
            case "dwg" ->
                bytes.length >= 6
                        && starts(bytes, "AC10")
                        && Character.isDigit(bytes[4])
                        && Character.isDigit(bytes[5]);
            case "heic" -> heic(bytes);
            case "docx", "xlsx" -> office(bytes, extension);
            default -> false;
        };
    }

    private boolean starts(byte[] bytes, String prefix) {
        return bytes.length >= prefix.length()
                && new String(bytes, 0, prefix.length(), StandardCharsets.US_ASCII).equals(prefix);
    }

    private boolean heic(byte[] bytes) {
        if (bytes.length < 16 || !new String(bytes, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) return false;
        int size = java.nio.ByteBuffer.wrap(bytes, 0, 4).getInt();
        if (size < 16 || size > bytes.length || size > 4096) return false;
        for (int i = 8; i + 4 <= size; i += 4) {
            if (i == 12) continue;
            String brand = new String(bytes, i, 4, StandardCharsets.US_ASCII);
            if (java.util.Set.of("heic", "heix", "hevc", "hevx").contains(brand)) return true;
        }
        return false;
    }

    private boolean office(byte[] bytes, String extension) throws IOException {
        if (!starts(bytes, "PK")) return false;
        boolean types = false, document = false;
        long expanded = 0;
        int count = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 10000) return false;
                if (entry.getName().equals("[Content_Types].xml")) types = true;
                if (entry.getName().equals(extension.equals("docx") ? "word/document.xml" : "xl/workbook.xml"))
                    document = true;
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 100L * 1024 * 1024) return false;
                }
            }
        } catch (IOException | IllegalArgumentException invalid) {
            return false;
        }
        return types && document;
    }

    private void fail(String message) {
        throw new UnprocessableInputException("file", message);
    }
}
