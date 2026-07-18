package com.huddle.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Consistent error body returned for every handled exception. {@code fieldErrors} is present only
 * for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(HttpStatus status, String message, String path) {
        return of(status.value(), status.getReasonPhrase(), message, path);
    }

    /**
     * Raw factory for a status that may not be one of the standard {@link HttpStatus} constants
     * (e.g. a {@code ResponseStatusException} carrying a non-standard code); {@code error} is the
     * caller-resolved reason phrase.
     */
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(HttpStatus status, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors.isEmpty() ? null : fieldErrors);
    }
}