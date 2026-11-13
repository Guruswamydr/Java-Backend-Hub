package com.example.coretracking.controller;

import com.example.coretracking.dto.ApiError;
import com.example.coretracking.dto.CoreResponse;
import com.example.coretracking.dto.CreateCoreRequest;
import com.example.coretracking.dto.PageResponse;
import com.example.coretracking.dto.UpdateStatusRequest;
import com.example.coretracking.entity.CoreStatus;
import com.example.coretracking.service.CoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP layer only: maps requests to the service and back. No business rules here.
 * Errors are turned into JSON by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/cores")
@Tag(name = "Cores", description = "Track returned engine cores")
public class CoreController {

    private final CoreService coreService;

    public CoreController(CoreService coreService) {
        this.coreService = coreService;
    }

    @PostMapping
    @Operation(summary = "Register a returned core", description = "Creates a core. Its status is always RECEIVED.")
    @ApiResponse(responseCode = "201", description = "Core created")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<CoreResponse> create(@Valid @RequestBody CreateCoreRequest request) {
        CoreResponse created = coreService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a core by id")
    @ApiResponse(responseCode = "200", description = "Core found")
    @ApiResponse(responseCode = "404", description = "No core with this id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CoreResponse getById(@PathVariable Long id) {
        return coreService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List cores (paged)",
            description = "Optional status filter. Pages are zero-based; default page size is 10 (maximum 100), "
                    + "default sort is by id.")
    @ApiResponse(responseCode = "200", description = "A page of cores")
    @ApiResponse(responseCode = "400", description = "Invalid status value or sort property",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<CoreResponse> list(
            @Parameter(description = "Only return cores in this status") @RequestParam(required = false) CoreStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return coreService.list(status, pageable);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update a core's status",
            description = "Allowed changes: RECEIVED to INSPECTED or SCRAPPED; INSPECTED to DISASSEMBLED or SCRAPPED; "
                    + "DISASSEMBLED to REMANUFACTURED or SCRAPPED. REMANUFACTURED and SCRAPPED are final. "
                    + "Anything else, including the same status, is rejected with 409.")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "400", description = "Missing or invalid status",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No core with this id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Status change not allowed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CoreResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return coreService.updateStatus(id, request.status());
    }
}
