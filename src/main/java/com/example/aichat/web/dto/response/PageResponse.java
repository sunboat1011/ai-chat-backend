package com.example.aichat.web.dto.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Boolean hasNext;

    public static <T> PageResponse<T> from(IPage<T> page) {
        return PageResponse.<T>builder()
            .content(page.getRecords())
            .page((int) page.getCurrent() - 1)
            .size((int) page.getSize())
            .totalElements(page.getTotal())
            .totalPages((int) page.getPages())
            .hasNext(page.getCurrent() < page.getPages())
            .build();
    }

    public <R> PageResponse<R> map(java.util.function.Function<T, R> mapper) {
        return PageResponse.<R>builder()
            .content(content.stream().map(mapper).toList())
            .page(page)
            .size(size)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .hasNext(hasNext)
            .build();
    }
}
