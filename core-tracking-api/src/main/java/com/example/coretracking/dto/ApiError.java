package com.example.coretracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The one JSON shape returned for every error (404, 409, 400, 405, 500, ...).
 * "fieldErrors" is only present for validation errors.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> fieldErrors) {

    /** One failed validation rule, for example field "partNumber", message "is required". */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(HttpStatus status, String message, String path, List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
