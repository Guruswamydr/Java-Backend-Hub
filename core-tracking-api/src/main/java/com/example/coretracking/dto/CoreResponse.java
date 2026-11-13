package com.example.coretracking.dto;

import com.example.coretracking.entity.Core;
import com.example.coretracking.entity.CoreStatus;

import java.time.LocalDate;

/** What the API returns for a core. The entity itself is never exposed. */
public record CoreResponse(
        Long id,
        String partNumber,
        String dealer,
        LocalDate receivedDate,
        CoreStatus status,
        String statusDisplayName) {

    public static CoreResponse from(Core core) {
        return new CoreResponse(
                core.getId(),
                core.getPartNumber(),
                core.getDealer(),
                core.getReceivedDate(),
                core.getStatus(),
                core.getStatus().getDisplayName());
    }
}
