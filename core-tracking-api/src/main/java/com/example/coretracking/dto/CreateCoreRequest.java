package com.example.coretracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for POST /api/cores. The status is not part of the request: a new core is always RECEIVED.
 * The "example" values below are illustrative only (they pre-fill Swagger UI).
 */
@Schema(description = "A core returned by a dealer")
public record CreateCoreRequest(

        @Schema(example = "PN-1001")
        @NotBlank(message = "is required")
        @Size(max = 50, message = "must be at most 50 characters")
        String partNumber,

        @Schema(example = "Dealer North")
        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String dealer,

        @Schema(example = "2026-11-12", description = "Date the core was received (today or earlier)")
        @NotNull(message = "is required")
        @PastOrPresent(message = "must not be in the future")
        LocalDate receivedDate) {
}
