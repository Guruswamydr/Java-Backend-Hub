package com.example.coretracking.exception;

/** Thrown when no core exists for the requested id. Mapped to HTTP 404 by GlobalExceptionHandler. */
public class CoreNotFoundException extends RuntimeException {

    public CoreNotFoundException(Long id) {
        super("Core with id " + id + " not found");
    }
}
