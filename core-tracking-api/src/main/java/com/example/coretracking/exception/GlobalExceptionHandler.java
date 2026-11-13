package com.example.coretracking.exception;

import com.example.coretracking.dto.ApiError;
import com.example.coretracking.dto.ApiError.FieldViolation;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Turns every exception into the same JSON error shape (ApiError) with the right HTTP status.
 *
 * It extends Spring's ResponseEntityExceptionHandler, which already knows the standard Spring MVC
 * exceptions (wrong HTTP method = 405, unsupported content type = 415, unknown URL = 404, ...).
 * A plain catch-all handler would turn those into 500s, so we customise this base class instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_SERVER_ERROR = "Unexpected server error. Please try again later or contact support.";

    // ------------------------------------------------------------------
    // Our own exceptions
    // ------------------------------------------------------------------

    /** Unknown id -> 404. */
    @ExceptionHandler(CoreNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(CoreNotFoundException ex, WebRequest request) {
        log.warn("404 on {}: {}", path(request), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /** Status change not allowed by the transition rules -> 409. */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Object> handleInvalidTransition(InvalidStatusTransitionException ex, WebRequest request) {
        log.warn("409 on {}: {}", path(request), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /** Sorting by a property that does not exist, for example ?sort=foo -> 400. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Object> handleInvalidSort(PropertyReferenceException ex, WebRequest request) {
        String message = "Invalid sort property '%s'".formatted(ex.getPropertyName());
        log.warn("400 on {}: {}", path(request), message);
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /** Anything we did not expect -> 500. The details go to the log, not to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("500 on {}", path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR, request, null);
    }

    // ------------------------------------------------------------------
    // Spring MVC exceptions: customised messages
    // ------------------------------------------------------------------

    /** Bean Validation failed on the request body (@Valid) -> 400 with one entry per bad field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        log.warn("400 on {}: validation failed {}", path(request), violations);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    /** Body is missing, is not JSON, or has a value of the wrong type (bad date, unknown status) -> 400. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = "Request body is missing or is not valid JSON";
        if (ex.getCause() instanceof InvalidFormatException invalid) {
            message = "Invalid value '%s' for field '%s'".formatted(invalid.getValue(), fieldName(invalid));
            Class<?> target = invalid.getTargetType();
            if (target != null && target.isEnum()) {
                message += ". Allowed values: " + Arrays.toString(target.getEnumConstants());
            }
        }
        log.warn("400 on {}: {}", path(request), message);
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /** A path variable or query parameter has the wrong type (id=abc, status=FOO) -> 400. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = (ex instanceof MethodArgumentTypeMismatchException mismatch)
                ? mismatch.getName()
                : ex.getPropertyName();
        String message = "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), name);
        Class<?> required = ex.getRequiredType();
        if (required != null && required.isEnum()) {
            message += ". Allowed values: " + Arrays.toString(required.getEnumConstants());
        }
        log.warn("400 on {}: {}", path(request), message);
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /**
     * Every other standard Spring MVC exception (405, 415, 404 for an unknown URL, ...) ends up here,
     * so they also come back in the ApiError shape.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message;
        if (status.is5xxServerError()) {
            log.error("{} on {}", status.value(), path(request), ex);
            message = GENERIC_SERVER_ERROR;
        } else {
            message = (ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null)
                    ? errorResponse.getBody().getDetail()
                    : status.getReasonPhrase();
            log.warn("{} on {}: {}", status.value(), path(request), message);
        }
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ApiError.of(status, message, path(request), null));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ResponseEntity<Object> build(HttpStatus status, String message, WebRequest request,
            List<FieldViolation> fieldErrors) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, path(request), fieldErrors));
    }

    private static String path(WebRequest request) {
        return (request instanceof ServletWebRequest servletRequest)
                ? servletRequest.getRequest().getRequestURI()
                : "";
    }

    /** Name of the JSON field that held the bad value, for example "status" or "receivedDate". */
    private static String fieldName(InvalidFormatException ex) {
        List<JsonMappingException.Reference> path = ex.getPath();
        if (path.isEmpty() || path.get(path.size() - 1).getFieldName() == null) {
            return "request body";
        }
        return path.get(path.size() - 1).getFieldName();
    }
}
