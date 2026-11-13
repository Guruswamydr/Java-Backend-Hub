package com.example.coretracking.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.example.coretracking.entity.CoreStatus.DISASSEMBLED;
import static com.example.coretracking.entity.CoreStatus.INSPECTED;
import static com.example.coretracking.entity.CoreStatus.RECEIVED;
import static com.example.coretracking.entity.CoreStatus.REMANUFACTURED;
import static com.example.coretracking.entity.CoreStatus.SCRAPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the status transition rules. No Spring and no database: just the enum.
 */
class CoreStatusTest {

    /**
     * The transition matrix written out independently of the production code.
     * This table IS the specification; the tests below check CoreStatus against it.
     */
    private static final Map<CoreStatus, Set<CoreStatus>> EXPECTED_ALLOWED = Map.of(
            RECEIVED, Set.of(INSPECTED, SCRAPPED),
            INSPECTED, Set.of(DISASSEMBLED, SCRAPPED),
            DISASSEMBLED, Set.of(REMANUFACTURED, SCRAPPED),
            REMANUFACTURED, Set.of(),
            SCRAPPED, Set.of());

    /** Every (from, to) pair: 5 x 5 = 25 combinations, each with the expected answer (6 allowed, 19 rejected). */
    static Stream<Arguments> everyTransition() {
        return Arrays.stream(CoreStatus.values()).flatMap(from ->
                Arrays.stream(CoreStatus.values()).map(to ->
                        Arguments.of(from, to, EXPECTED_ALLOWED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed = {2}")
    @MethodSource("everyTransition")
    void canTransitionTo_matchesTheTransitionMatrix(CoreStatus from, CoreStatus to, boolean expectedAllowed) {
        assertEquals(expectedAllowed, from.canTransitionTo(to));
    }

    @Test
    void theMatrixCoversAllTwentyFiveCombinations_sixAllowedAndNineteenRejected() {
        assertEquals(25, everyTransition().count());
        assertEquals(6, everyTransition().filter(args -> (boolean) args.get()[2]).count());
        assertEquals(19, everyTransition().filter(args -> !(boolean) args.get()[2]).count());
    }

    @ParameterizedTest
    @EnumSource(CoreStatus.class)
    void allowedNextStatuses_matchesTheMatrix(CoreStatus status) {
        assertEquals(EXPECTED_ALLOWED.get(status), status.allowedNextStatuses());
    }

    @ParameterizedTest
    @EnumSource(CoreStatus.class)
    void aSameStatusChangeIsNeverAllowed(CoreStatus status) {
        assertFalse(status.canTransitionTo(status));
    }

    @ParameterizedTest
    @EnumSource(value = CoreStatus.class, names = {"REMANUFACTURED", "SCRAPPED"})
    void terminalStatusesAllowNoFurtherChange(CoreStatus terminal) {
        assertTrue(terminal.allowedNextStatuses().isEmpty());
        for (CoreStatus target : CoreStatus.values()) {
            assertFalse(terminal.canTransitionTo(target), terminal + " -> " + target + " must be rejected");
        }
    }

    @Test
    void displayNamesAreHumanReadable() {
        assertEquals("Received", RECEIVED.getDisplayName());
        assertEquals("Inspected", INSPECTED.getDisplayName());
        assertEquals("Disassembled", DISASSEMBLED.getDisplayName());
        assertEquals("Remanufactured", REMANUFACTURED.getDisplayName());
        assertEquals("Scrapped", SCRAPPED.getDisplayName());
    }
}
