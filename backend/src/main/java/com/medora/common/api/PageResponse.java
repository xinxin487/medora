package com.medora.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> list,
        long total,
        int pageNo,
        int pageSize,
        long totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
