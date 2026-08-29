package com.pureeats.domain.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * A plain, explicitly-serializable pagination envelope - deliberately not Spring Data's
 * {@code Page}/{@code PageImpl} directly, since returning that type over the wire ties the JSON
 * shape to Spring Data's internal representation (and its Jackson (de)serialization support has
 * shifted across versions). Any endpoint returning a page of results can reuse this.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
