package com.example.coretracking.service;

import com.example.coretracking.dto.CoreResponse;
import com.example.coretracking.dto.CreateCoreRequest;
import com.example.coretracking.dto.PageResponse;
import com.example.coretracking.entity.Core;
import com.example.coretracking.entity.CoreStatus;
import com.example.coretracking.exception.CoreNotFoundException;
import com.example.coretracking.exception.InvalidStatusTransitionException;
import com.example.coretracking.repository.CoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service tests with a mocked repository: no Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
class CoreServiceTest {

    @Mock
    private CoreRepository coreRepository;

    @InjectMocks
    private CoreService coreService;

    // ------------------------------------------------------------------ create

    @Test
    void create_savesANewCoreInStatusReceived_andReturnsItAsDto() {
        // the database would generate the id, so the mock sets one
        when(coreRepository.save(any(Core.class))).thenAnswer(invocation -> {
            Core core = invocation.getArgument(0);
            ReflectionTestUtils.setField(core, "id", 42L);
            return core;
        });

        CoreResponse response = coreService.create(
                new CreateCoreRequest("PN-100", "Dealer A", LocalDate.of(2026, 11, 1)));

        ArgumentCaptor<Core> saved = ArgumentCaptor.forClass(Core.class);
        verify(coreRepository).save(saved.capture());
        assertEquals(CoreStatus.RECEIVED, saved.getValue().getStatus());
        assertEquals("PN-100", saved.getValue().getPartNumber());
        assertEquals("Dealer A", saved.getValue().getDealer());
        assertEquals(LocalDate.of(2026, 11, 1), saved.getValue().getReceivedDate());

        assertEquals(42L, response.id());
        assertEquals(CoreStatus.RECEIVED, response.status());
        assertEquals("Received", response.statusDisplayName());
    }

    // ------------------------------------------------------------------ getById

    @Test
    void getById_returnsTheCoreAsDto_whenItExists() {
        when(coreRepository.findById(5L)).thenReturn(Optional.of(coreWithStatus(5L, CoreStatus.INSPECTED)));

        CoreResponse response = coreService.getById(5L);

        assertEquals(5L, response.id());
        assertEquals("PN-100", response.partNumber());
        assertEquals(CoreStatus.INSPECTED, response.status());
        assertEquals("Inspected", response.statusDisplayName());
    }

    @Test
    void getById_throwsNotFound_whenTheIdIsUnknown() {
        when(coreRepository.findById(99L)).thenReturn(Optional.empty());

        CoreNotFoundException ex = assertThrows(CoreNotFoundException.class, () -> coreService.getById(99L));

        assertEquals("Core with id 99 not found", ex.getMessage());
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_withoutStatus_returnsAllCoresAndPagingInfo() {
        Pageable pageable = PageRequest.of(0, 2);
        List<Core> cores = List.of(coreWithStatus(1L, CoreStatus.RECEIVED), coreWithStatus(2L, CoreStatus.SCRAPPED));
        when(coreRepository.findAll(pageable)).thenReturn(new PageImpl<>(cores, pageable, 5));

        PageResponse<CoreResponse> page = coreService.list(null, pageable);

        assertEquals(2, page.content().size());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        verify(coreRepository, never()).findByStatus(any(), any());
    }

    @Test
    void list_withStatus_usesTheStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Core> cores = List.of(coreWithStatus(3L, CoreStatus.INSPECTED));
        when(coreRepository.findByStatus(CoreStatus.INSPECTED, pageable))
                .thenReturn(new PageImpl<>(cores, pageable, 1));

        PageResponse<CoreResponse> page = coreService.list(CoreStatus.INSPECTED, pageable);

        assertEquals(1, page.content().size());
        assertEquals(CoreStatus.INSPECTED, page.content().get(0).status());
        verify(coreRepository, never()).findAll(any(Pageable.class));
    }

    // ------------------------------------------------------------------ updateStatus

    @ParameterizedTest(name = "{0} -> {1} is applied and saved")
    @CsvSource({
            "RECEIVED, INSPECTED",
            "RECEIVED, SCRAPPED",
            "INSPECTED, DISASSEMBLED",
            "INSPECTED, SCRAPPED",
            "DISASSEMBLED, REMANUFACTURED",
            "DISASSEMBLED, SCRAPPED"
    })
    void updateStatus_appliesEveryAllowedTransition(CoreStatus from, CoreStatus to) {
        Core core = coreWithStatus(7L, from);
        when(coreRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(core));

        CoreResponse response = coreService.updateStatus(7L, to);

        assertEquals(to, response.status());
        assertEquals(to, core.getStatus());
        verify(coreRepository).save(core);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected and nothing is saved")
    @CsvSource({
            "SCRAPPED, RECEIVED",         // leaving a final status
            "REMANUFACTURED, SCRAPPED",   // leaving a final status
            "RECEIVED, DISASSEMBLED",     // skipping a step
            "INSPECTED, REMANUFACTURED",  // skipping a step
            "DISASSEMBLED, INSPECTED",    // going backwards
            "INSPECTED, INSPECTED"        // same status
    })
    void updateStatus_rejectsDisallowedTransitions_andDoesNotSave(CoreStatus from, CoreStatus to) {
        Core core = coreWithStatus(7L, from);
        when(coreRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(core));

        assertThrows(InvalidStatusTransitionException.class, () -> coreService.updateStatus(7L, to));

        assertEquals(from, core.getStatus()); // unchanged
        verify(coreRepository, never()).save(any(Core.class));
    }

    @Test
    void updateStatus_errorMessageNamesBothStatuses_andListsTheAllowedOnes() {
        when(coreRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coreWithStatus(1L, CoreStatus.SCRAPPED)));
        when(coreRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(coreWithStatus(2L, CoreStatus.INSPECTED)));

        InvalidStatusTransitionException fromTerminal = assertThrows(InvalidStatusTransitionException.class,
                () -> coreService.updateStatus(1L, CoreStatus.RECEIVED));
        InvalidStatusTransitionException fromInspected = assertThrows(InvalidStatusTransitionException.class,
                () -> coreService.updateStatus(2L, CoreStatus.RECEIVED));

        assertEquals("Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none",
                fromTerminal.getMessage());
        assertEquals("Cannot change status from INSPECTED to RECEIVED. Allowed next statuses: DISASSEMBLED, SCRAPPED",
                fromInspected.getMessage());
    }

    @Test
    void updateStatus_throwsNotFound_whenTheIdIsUnknown() {
        when(coreRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(CoreNotFoundException.class, () -> coreService.updateStatus(99L, CoreStatus.INSPECTED));

        verify(coreRepository, never()).save(any(Core.class));
    }

    // ------------------------------------------------------------------ helper

    /** A core as it would look after being loaded from the database (with an id). */
    private static Core coreWithStatus(long id, CoreStatus status) {
        Core core = new Core("PN-100", "Dealer A", LocalDate.of(2026, 11, 1));
        core.setStatus(status);
        ReflectionTestUtils.setField(core, "id", id);
        return core;
    }
}
