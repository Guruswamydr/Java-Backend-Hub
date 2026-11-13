package com.example.coretracking.controller;

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

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the REST endpoints: real Spring context, real H2 database, requests sent with MockMvc
 * (no network port is opened).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CoreRepository coreRepository;

    @BeforeEach
    void startWithAnEmptyTable() {
        // data.sql loads sample rows at start-up. Every test starts from an empty table so counts are predictable.
        coreRepository.deleteAllInBatch();
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_returns201_withLocationHeader_andStatusReceived() throws Exception {
        LocalDate today = LocalDate.now();
        String body = """
                {"partNumber": "PN-100", "dealer": "Dealer A", "receivedDate": "%s"}
                """.formatted(today);

        mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/cores/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.partNumber").value("PN-100"))
                .andExpect(jsonPath("$.dealer").value("Dealer A"))
                .andExpect(jsonPath("$.receivedDate").value(today.toString()))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.statusDisplayName").value("Received"));

        assertEquals(1, coreRepository.count());
    }

    @Test
    void create_ignoresAStatusSentByTheClient_aNewCoreIsAlwaysReceived() throws Exception {
        String body = """
                {"partNumber": "PN-100", "dealer": "Dealer A", "receivedDate": "2026-10-01", "status": "SCRAPPED"}
                """;

        mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void create_withInvalidBody_returns400_listingEveryBadField() throws Exception {
        // blank partNumber, dealer missing, receivedDate in the future
        String body = """
                {"partNumber": " ", "receivedDate": "%s"}
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/cores"))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("partNumber", "dealer", "receivedDate")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='receivedDate')].message").value("must not be in the future"));

        assertEquals(0, coreRepository.count());
    }

    @Test
    void create_withATooLongPartNumber_returns400() throws Exception {
        String body = """
                {"partNumber": "%s", "dealer": "Dealer A", "receivedDate": "2026-10-01"}
                """.formatted("P".repeat(51));

        mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("partNumber"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be at most 50 characters"));
    }

    @Test
    void create_withAnUnreadableDate_returns400() throws Exception {
        String body = """
                {"partNumber": "PN-1", "dealer": "Dealer A", "receivedDate": "not-a-date"}
                """;

        mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'not-a-date' for field 'receivedDate'"));
    }

    // ------------------------------------------------------------------ get by id

    @Test
    void getById_returnsTheCore() throws Exception {
        Core core = save("PN-200", "Dealer B", CoreStatus.INSPECTED);

        mockMvc.perform(get("/api/cores/{id}", core.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(core.getId()))
                .andExpect(jsonPath("$.partNumber").value("PN-200"))
                .andExpect(jsonPath("$.dealer").value("Dealer B"))
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.statusDisplayName").value("Inspected"));
    }

    @Test
    void getById_unknownId_returns404_withAJsonError() throws Exception {
        mockMvc.perform(get("/api/cores/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Core with id 999999 not found"))
                .andExpect(jsonPath("$.path").value("/api/cores/999999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/cores/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'id'"));
    }

    // ------------------------------------------------------------------ list: pagination and status filter

    @Test
    void list_returnsPagesOfTheRequestedSize() throws Exception {
        for (int i = 1; i <= 5; i++) {
            save("PN-" + i, "Dealer A", CoreStatus.RECEIVED);
        }

        mockMvc.perform(get("/api/cores").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].partNumber").value("PN-1"))
                .andExpect(jsonPath("$.content[1].partNumber").value("PN-2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        // the last page holds the remainder
        mockMvc.perform(get("/api/cores").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].partNumber").value("PN-5"));
    }

    @Test
    void list_withoutParameters_usesTheDefaultPageSizeOfTen() throws Exception {
        for (int i = 1; i <= 12; i++) {
            save("PN-" + i, "Dealer A", CoreStatus.RECEIVED);
        }

        mockMvc.perform(get("/api/cores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_canBeSortedByAnotherProperty() throws Exception {
        save("PN-1", "Dealer A", CoreStatus.RECEIVED, LocalDate.of(2026, 10, 1));
        save("PN-2", "Dealer A", CoreStatus.RECEIVED, LocalDate.of(2026, 10, 3));
        save("PN-3", "Dealer A", CoreStatus.RECEIVED, LocalDate.of(2026, 10, 2));

        mockMvc.perform(get("/api/cores").param("sort", "receivedDate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].partNumber", contains("PN-2", "PN-3", "PN-1")));
    }

    @Test
    void list_sanitisesOutOfRangePagingParameters() throws Exception {
        // size above the maximum is capped (spring.data.web.pageable.max-page-size=100)
        mockMvc.perform(get("/api/cores").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        // size 0 is not allowed: the default size is used instead
        mockMvc.perform(get("/api/cores").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));

        // a negative page number becomes page 0
        mockMvc.perform(get("/api/cores").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_filtersByStatus() throws Exception {
        save("PN-1", "Dealer A", CoreStatus.RECEIVED);
        save("PN-2", "Dealer A", CoreStatus.RECEIVED);
        save("PN-3", "Dealer B", CoreStatus.INSPECTED);
        save("PN-4", "Dealer B", CoreStatus.SCRAPPED);
        save("PN-5", "Dealer C", CoreStatus.SCRAPPED);

        mockMvc.perform(get("/api/cores").param("status", "SCRAPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].status", everyItem(equalTo("SCRAPPED"))));

        // no filter: everything comes back
        mockMvc.perform(get("/api/cores"))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void list_statusFilterAndPaginationWorkTogether() throws Exception {
        save("PN-1", "Dealer A", CoreStatus.RECEIVED);
        save("PN-2", "Dealer A", CoreStatus.RECEIVED);
        save("PN-3", "Dealer A", CoreStatus.RECEIVED);
        save("PN-4", "Dealer A", CoreStatus.INSPECTED);

        mockMvc.perform(get("/api/cores").param("status", "RECEIVED").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].partNumber").value("PN-3"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_withAnUnknownStatusFilter_returns400_listingTheAllowedValues() throws Exception {
        mockMvc.perform(get("/api/cores").param("status", "SHIPPED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'SHIPPED' for parameter 'status'. "
                        + "Allowed values: [RECEIVED, INSPECTED, DISASSEMBLED, REMANUFACTURED, SCRAPPED]"));
    }

    @Test
    void list_withAnUnknownSortProperty_returns400() throws Exception {
        save("PN-1", "Dealer A", CoreStatus.RECEIVED);

        mockMvc.perform(get("/api/cores").param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property 'foo'"));
    }

    // ------------------------------------------------------------------ update status (PATCH)

    @Test
    void updateStatus_allowedChange_returns200_andIsSaved() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.RECEIVED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("INSPECTED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(core.getId()))
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.statusDisplayName").value("Inspected"));

        assertEquals(CoreStatus.INSPECTED, coreRepository.findById(core.getId()).orElseThrow().getStatus());
    }

    @Test
    void updateStatus_fromAFinalStatus_returns409_withAClearMessage() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.SCRAPPED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("RECEIVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none"))
                .andExpect(jsonPath("$.path").value("/api/cores/" + core.getId() + "/status"));

        assertEquals(CoreStatus.SCRAPPED, coreRepository.findById(core.getId()).orElseThrow().getStatus());
    }

    @Test
    void updateStatus_toTheSameStatus_returns409() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.INSPECTED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("INSPECTED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change status from INSPECTED to INSPECTED. Allowed next statuses: DISASSEMBLED, SCRAPPED"));
    }

    @Test
    void updateStatus_skippingAStep_returns409() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.RECEIVED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("DISASSEMBLED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change status from RECEIVED to DISASSEMBLED. Allowed next statuses: INSPECTED, SCRAPPED"));

        assertEquals(CoreStatus.RECEIVED, coreRepository.findById(core.getId()).orElseThrow().getStatus());
    }

    @Test
    void updateStatus_unknownId_returns404() throws Exception {
        mockMvc.perform(patch("/api/cores/{id}/status", 999999)
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("INSPECTED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Core with id 999999 not found"));
    }

    @Test
    void updateStatus_withoutAStatus_returns400() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.RECEIVED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("is required"));
    }

    @Test
    void updateStatus_withAnUnknownStatusValue_returns400_listingTheAllowedValues() throws Exception {
        Core core = save("PN-1", "Dealer A", CoreStatus.RECEIVED);

        mockMvc.perform(patch("/api/cores/{id}/status", core.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("SHIPPED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'SHIPPED' for field 'status'. "
                        + "Allowed values: [RECEIVED, INSPECTED, DISASSEMBLED, REMANUFACTURED, SCRAPPED]"));
    }

    @Test
    void lifecycle_receivedToRemanufactured_thenNoFurtherChangeIsPossible() throws Exception {
        String createBody = """
                {"partNumber": "PN-9", "dealer": "Dealer A", "receivedDate": "2026-10-01"}
                """;
        String location = mockMvc.perform(post("/api/cores").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String statusUrl = location + "/status";

        for (String next : new String[] {"INSPECTED", "DISASSEMBLED", "REMANUFACTURED"}) {
            mockMvc.perform(patch(statusUrl).contentType(MediaType.APPLICATION_JSON).content(statusBody(next)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
        }

        // REMANUFACTURED is final, even SCRAPPED is no longer possible
        mockMvc.perform(patch(statusUrl).contentType(MediaType.APPLICATION_JSON).content(statusBody("SCRAPPED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change status from REMANUFACTURED to SCRAPPED. Allowed next statuses: none"));
    }

    // ------------------------------------------------------------------ other errors and API docs

    @Test
    void unsupportedHttpMethod_returns405_inTheSameJsonShape() throws Exception {
        mockMvc.perform(delete("/api/cores/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/api/cores/1"));
    }

    @Test
    void swaggerUiAndOpenApiDocs_areAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Core Tracking API"))
                .andExpect(jsonPath("$.paths['/api/cores']").exists())
                .andExpect(jsonPath("$.paths['/api/cores/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/cores/{id}/status']").exists());
    }

    // ------------------------------------------------------------------ helpers

    private Core save(String partNumber, String dealer, CoreStatus status) {
        return save(partNumber, dealer, status, LocalDate.of(2026, 10, 1));
    }

    private Core save(String partNumber, String dealer, CoreStatus status, LocalDate receivedDate) {
        Core core = new Core(partNumber, dealer, receivedDate);
        core.setStatus(status);
        return coreRepository.save(core);
    }

    private static String statusBody(String status) {
        return "{\"status\": \"" + status + "\"}";
    }
}
