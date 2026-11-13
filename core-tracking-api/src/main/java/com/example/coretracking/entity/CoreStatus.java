package com.example.coretracking.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a returned engine core.
 *
 * The status transition rules live in ONE place: {@link #allowedNextStatuses()}.
 * The service, the error messages and the tests all ask this enum, so the rules cannot drift apart.
 */
public enum CoreStatus {

    RECEIVED("Received"),
    INSPECTED("Inspected"),
    DISASSEMBLED("Disassembled"),
    REMANUFACTURED("Remanufactured"),
    SCRAPPED("Scrapped");

    private final String displayName;

    CoreStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * The statuses a core in this status may move to. Empty for the terminal statuses.
     *
     *   RECEIVED       -> INSPECTED or SCRAPPED
     *   INSPECTED      -> DISASSEMBLED or SCRAPPED
     *   DISASSEMBLED   -> REMANUFACTURED or SCRAPPED
     *   REMANUFACTURED -> none (terminal)
     *   SCRAPPED       -> none (terminal)
     */
    public Set<CoreStatus> allowedNextStatuses() {
        // A switch EXPRESSION must cover every constant, so adding a new status without
        // deciding its rules is a compile error.
        return switch (this) {
            case RECEIVED -> EnumSet.of(INSPECTED, SCRAPPED);
            case INSPECTED -> EnumSet.of(DISASSEMBLED, SCRAPPED);
            case DISASSEMBLED -> EnumSet.of(REMANUFACTURED, SCRAPPED);
            case REMANUFACTURED, SCRAPPED -> EnumSet.noneOf(CoreStatus.class);
        };
    }

    /**
     * True if a core in this status may be changed to {@code target}.
     * A same-status change is never allowed: no status is in its own allowed set.
     */
    public boolean canTransitionTo(CoreStatus target) {
        return allowedNextStatuses().contains(target);
    }
}
