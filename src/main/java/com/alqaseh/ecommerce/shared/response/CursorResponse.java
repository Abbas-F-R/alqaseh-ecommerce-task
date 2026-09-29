package com.alqaseh.ecommerce.shared.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Standardized cursor-paginated response envelope")
public record CursorResponse<T>(
        @Schema(description = "Rows of this page (empty when nothing matches)") List<T> data,
        @Schema(description = "Opaque cursor token pointing to the next page, or null if no further items") String nextCursor,
        @JsonProperty("hasMore") @Schema(description = "Whether more items exist after this page") boolean hasMore
) {
}
