package com.dcdev.pt.item.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Explicit page envelope: {@code {content, page: {number, size, totalElements, totalPages}}}.
 *
 * <p>Deliberately not Spring Data's {@code Page} serialised directly — that shape
 * is an implementation detail that has changed across Spring Data versions. The
 * nested {@code page} object (rather than flat {@code page}/{@code size}/... fields)
 * is the agreed API contract for this exercise: it keeps every pagination fact
 * under one key, so a client can pass the whole {@code page} object back to the
 * "load next page" call without picking fields out of the root.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new PageMetadata(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
