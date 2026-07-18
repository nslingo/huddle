package com.huddle.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable, DTO-friendly pagination envelope. Avoids serializing Spring Data's {@code Page}
 * implementation directly, whose JSON shape is not contract-stable.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    /** Builds an envelope from already-mapped {@code content} plus a source page's metadata. */
    public static <T> PageResponse<T> of(List<T> content, Page<?> source) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext());
    }
}