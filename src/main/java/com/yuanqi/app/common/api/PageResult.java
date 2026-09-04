package com.yuanqi.app.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "PageResult")
public record PageResult<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages,
                            boolean hasPrevious, boolean hasNext) {
    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        int pages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(items, page, pageSize, total, pages, page > 1, page < pages);
    }
}
