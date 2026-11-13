package com.example.coretracking.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for a page of results. Spring Data's own Page object is not returned directly
 * because its JSON structure is not guaranteed to stay the same between versions.
 * "page" is zero-based.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
