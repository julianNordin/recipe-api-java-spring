package com.recipeapi.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The paging envelope this API returns.
 *
 * <p>Deliberately not Spring's {@link Page}. Serialising a {@code Page} directly produces a large
 * JSON object whose shape is an implementation detail - Spring itself warns that it is unstable
 * across versions - and it leaks Pageable and Sort internals into the public contract. Declaring
 * the envelope here means the response shape changes when this project decides to change it, not
 * when a Spring upgrade does.
 *
 * @param content       the page's items
 * @param page          zero-based page number
 * @param size          the requested page size
 * @param totalElements how many items match across every page
 * @param totalPages    how many pages that works out to
 * @param first         true when this is the first page
 * @param last          true when this is the last page
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
