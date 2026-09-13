package rw.centrika.orders.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rw.centrika.orders.dto.ApiError;

import java.util.List;

/**
 * Every handler here returns the same ApiError shape (see dto/ApiError),
 * so API consumers never have to special-case how a 400 looks versus a
 * 404 or a 409 — only the status code and message change.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();

        ApiError body = ApiError.ofValidation(
            HttpStatus.BAD_REQUEST.value(),
            "One or more fields are invalid",
            req.getRequestURI(),
            fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        // 409 Conflict: the request was well-formed, but the current
        // state of the resource (stock level) prevents it from succeeding.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError.of(HttpStatus.CONFLICT.value(), "Insufficient Stock", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError.of(HttpStatus.CONFLICT.value(), "Invalid Status Transition", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
            ApiError.of(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Log the real exception server-side with full detail — the
        // client only ever gets the generic message below, so nothing
        // about the internal cause (SQL, stack trace, field names) leaks
        // in the HTTP response.
        log.error("Unexpected error handling {} {}", req.getMethod(), req.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                "An unexpected error occurred", req.getRequestURI()));
    }
}