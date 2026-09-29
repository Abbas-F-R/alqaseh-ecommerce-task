package com.alqaseh.ecommerce.shared.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Standardized paginated response envelope")
public record PageResponse<T>(
        @Schema(description = "Rows of this page (empty when nothing matches)") List<T> data,
        @Schema(description = "Total number of pages") int pagesCount,
        @Schema(description = "Zero-based page number") int currentPage,
        @Schema(description = "Number of rows over all pages") long totalCount,
        @JsonProperty("isLast") @Schema(description = "Whether this is the last page") boolean isLast
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalPages(),
                page.getNumber(),
                page.getTotalElements(),
                page.isLast()
        );
    }
}
