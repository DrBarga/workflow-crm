package dev.workflowcrm.shared.read;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.List;

public record ReadPage(int page, int size) {
    public ReadPage {
        if (page < 0 || page > 100000)
            throw new UnprocessableInputException("page", "Page must be between 0 and 100000");
        if (size < 1 || size > 100) throw new UnprocessableInputException("size", "Size must be between 1 and 100");
    }

    public long offset() {
        return (long) page * size;
    }

    public <T> Result<T> result(List<T> items, long total) {
        return new Result<>(items, page, size, total, offset() + items.size() < total);
    }

    public record Result<T>(List<T> items, int page, int size, long totalElements, boolean hasMore) {
        public Result {
            items = List.copyOf(items);
        }
    }
}
