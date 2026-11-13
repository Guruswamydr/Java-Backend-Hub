package com.example.coretracking.exception;

import com.example.coretracking.entity.CoreStatus;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thrown when a status change breaks the transition rules (including a change to the same status).
 * Mapped to HTTP 409 by GlobalExceptionHandler.
 * Example message: "Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none"
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(CoreStatus from, CoreStatus to) {
        super("Cannot change status from %s to %s. Allowed next statuses: %s"
                .formatted(from, to, describe(from.allowedNextStatuses())));
    }

    private static String describe(Set<CoreStatus> allowed) {
        if (allowed.isEmpty()) {
            return "none";
        }
        return allowed.stream().map(Enum::name).collect(Collectors.joining(", "));
    }
}
