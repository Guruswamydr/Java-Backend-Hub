package com.example.coretracking.service;

import com.example.coretracking.entity.Core;
import com.example.coretracking.entity.CoreStatus;
import com.example.coretracking.repository.CoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * Proves that the row lock in CoreService.updateStatus works: two updates of the same core cannot both
 * validate against the same old status.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreStatusConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CoreRepository coreRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void startWithAnEmptyTable() {
        coreRepository.deleteAllInBatch();
    }

    /**
     * Transaction A locks a core, changes it INSPECTED -> SCRAPPED and holds the lock for a moment.
     * Meanwhile a PATCH request asks for INSPECTED -> DISASSEMBLED on the same core.
     * Expected: the PATCH waits for A, then sees SCRAPPED (not the stale INSPECTED) and is rejected with 409.
     * Without the row lock the PATCH would validate against the stale INSPECTED, wait for A at its UPDATE, then
     * overwrite SCRAPPED with DISASSEMBLED and answer 200: a "lost update" that breaks the transition rules.
     * (Checked by temporarily replacing findByIdForUpdate with findById: this test then fails with 200 instead of 409.)
     */
    @Test
    void aStatusUpdateWaitsForTheRowLock_thenValidatesAgainstTheLatestStatus() throws Exception {
        Core core = new Core("PN-1", "Dealer A", LocalDate.of(2026, 10, 1));
        core.setStatus(CoreStatus.INSPECTED);
        Long id = coreRepository.save(core).getId();

        CountDownLatch rowIsLocked = new CountDownLatch(1);
        CountDownLatch letTransactionAFinish = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            // Transaction A: lock the row, set SCRAPPED, keep the lock until told to commit.
            Future<?> transactionA = threads.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                        Core locked = coreRepository.findByIdForUpdate(id).orElseThrow();
                        locked.setStatus(CoreStatus.SCRAPPED);
                        coreRepository.saveAndFlush(locked);
                        rowIsLocked.countDown();
                        waitFor(letTransactionAFinish);
                    }));
            assertTrue(rowIsLocked.await(10, TimeUnit.SECONDS), "transaction A should have locked the row");

            // The PATCH request, on another thread.
            Future<MvcResult> patchRequest = threads.submit(() ->
                    mockMvc.perform(patch("/api/cores/{id}/status", id)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\": \"DISASSEMBLED\"}"))
                            .andReturn());

            Thread.sleep(500);
            assertFalse(patchRequest.isDone(), "the PATCH must still be waiting while transaction A holds the row");

            letTransactionAFinish.countDown(); // A commits: the core is now SCRAPPED
            transactionA.get(10, TimeUnit.SECONDS);

            MvcResult result = patchRequest.get(10, TimeUnit.SECONDS);
            assertEquals(409, result.getResponse().getStatus());
            assertTrue(result.getResponse().getContentAsString()
                    .contains("Cannot change status from SCRAPPED to DISASSEMBLED"));
            assertEquals(CoreStatus.SCRAPPED, coreRepository.findById(id).orElseThrow().getStatus());
        } finally {
            letTransactionAFinish.countDown(); // never leave a thread waiting if an assertion failed
            threads.shutdownNow();
        }
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
