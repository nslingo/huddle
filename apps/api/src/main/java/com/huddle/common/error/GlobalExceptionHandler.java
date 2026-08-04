package com.huddle.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into a consistent {@link ApiError} JSON body. Unexpected errors are logged
 * server-side and reported generically.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean-validation failures on {@code @Valid} request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.of(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), fieldErrors));
    }

    /** Bean-validation failures on {@code @Validated} method params (e.g. query params). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.of(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), fieldErrors));
    }

    /** A request param that can't be converted to its target type (e.g. {@code ?page=abc}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        return ResponseEntity.badRequest().body(
                ApiError.of(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
    }

    /**
     * Wrong HTTP method for a mapped path. Sets the {@code Allow} header from the supported methods,
     * as RFC 9110 §15.5.6 requires for a 405 (Spring's default handler does this; overriding it here
     * would otherwise drop the header).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null) {
            builder.allow(supported.toArray(HttpMethod[]::new));
        }
        return builder.body(
                ApiError.of(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request.getRequestURI()));
    }

    /** A well-formed request for a resource that doesn't exist (e.g. an unknown club public id). */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Missing, malformed, expired or otherwise unverifiable credentials. Carries the
     * {@code WWW-Authenticate: Bearer} challenge RFC 6750 §3 requires of a 401 from a bearer-token
     * resource.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI()));
    }

    /** Authenticated but not permitted — e.g. a valid Google token from a non-Cornell account. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiError.of(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI()));
    }

    /** Unknown path / no matching handler. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(HttpStatus.NOT_FOUND, "Resource not found", request.getRequestURI()));
    }

    /**
     * Any status-bearing exception (e.g. Spring 6's {@code HandlerMethodValidationException} raised
     * for method-parameter validation) — honor its status rather than falling through to 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reasonPhrase = resolved != null ? resolved.getReasonPhrase() : "";
        String message = ex.getReason() != null ? ex.getReason() : reasonPhrase;
        return ResponseEntity.status(status)
                .headers(ex.getHeaders())
                .body(ApiError.of(status.value(), reasonPhrase, message, request.getRequestURI()));
    }

    /** Anything unhandled: 500 with a generic message (never a stack trace). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                        request.getRequestURI()));
    }

    private static ApiError.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new ApiError.FieldError(field, violation.getMessage());
    }
}