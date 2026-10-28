package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.UUID;

public final class ObjectExport {
    private ObjectExport() {}

    public enum Format {
        CSV("csv", "text/csv; charset=UTF-8"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    public enum State {
        QUEUED,
        READY,
        FAILED,
        EXPIRED
    }

    public record Job(
            UUID id,
            UUID actor,
            Format format,
            ObjectListFilter filter,
            ObjectListScope scope,
            Instant requestedAt,
            Instant expiresAt,
            String bucket,
            String key,
            State state,
            long rows,
            String error,
            int attempts) {
        public String filename() {
            return "objects-" + id + "." + format.extension();
        }

        public View view(Instant now) {
            return new View(
                    id, now.isBefore(expiresAt) ? state : State.EXPIRED, format, requestedAt, expiresAt, rows, error);
        }
    }

    public record View(
            UUID id, State state, Format format, Instant requestedAt, Instant expiresAt, long rows, String error) {}

    public record Download(String url, Instant expiresAt) {}
}
