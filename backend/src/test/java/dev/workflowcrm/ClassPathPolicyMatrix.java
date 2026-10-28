package dev.workflowcrm;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class ClassPathPolicyMatrix {
    List<String> read() throws Exception {
        try (var stream = getClass().getResourceAsStream("/objects/t6712-transition-matrix.txt")) {
            if (stream == null) throw new IllegalStateException("T6712 transition matrix is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
    }
}
