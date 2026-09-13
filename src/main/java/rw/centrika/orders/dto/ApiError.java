package rw.centrika.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uniform error shape for every 4xx/5xx response, so API consumers only
 * ever need to parse one structure regardless of what went wrong.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ApiError ofValidation(int status, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status, "Validation Failed", message, path, fieldErrors);
    }
}
