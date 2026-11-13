package com.example.coretracking.dto;

import com.example.coretracking.entity.CoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/cores/{id}/status. */
@Schema(description = "The status the core should move to")
public record UpdateStatusRequest(

        @Schema(example = "INSPECTED")
        @NotNull(message = "is required")
        CoreStatus status) {
}
