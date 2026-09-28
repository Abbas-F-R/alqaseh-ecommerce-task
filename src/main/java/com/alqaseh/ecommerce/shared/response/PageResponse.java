package com.alqaseh.ecommerce.shared.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of a list")
public record PageResponse<T>(
        @Schema(description = "Rows of this page (empty when nothing matches)") List<T> content,
        @Schema(description = "Zero-based page number") int pageNumber,
        @Schema(description = "Requested page size") int pageSize,
        @Schema(description = "Number of rows over all pages") long totalElements,
        @Schema(description = "Number of pages") int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), page.hasNext(), page.hasPrevious());
    }
}
