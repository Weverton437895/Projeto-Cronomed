package br.com.cronomed.common;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String code, String message, List<FieldIssue> fields) {

    public record FieldIssue(String field, String message) {
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, List.of());
    }
}
