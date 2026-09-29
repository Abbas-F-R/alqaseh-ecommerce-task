package com.alqaseh.ecommerce.features.product.dto.request;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.alqaseh.ecommerce.features.product.validation.ValidCursor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer product list: filters plus cursor (keyset) pagination, made for browsing a large catalogue page after page. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filters and cursor (keyset) pagination for the customer product list")
public class CustomerProductFilterRequest implements ProductCriteria {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    @Schema(description = "Name contains (case-insensitive)")
    @Size(max = 150, message = "Name filter cannot exceed 150 characters")
    @Pattern(regexp = "\\P{Cc}*", message = "Name filter must not contain control characters")
    private String name;

    @Schema(description = "Product category (any letter case)")
    private ProductCategory category;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = MAX_LIMIT, message = "Limit cannot exceed " + MAX_LIMIT)
    @Schema(description = "Maximum number of items to return (1-50)", defaultValue = "10", minimum = "1", maximum = "50")
    @Builder.Default
    private Integer limit = DEFAULT_LIMIT;

    @ValidCursor
    @Size(max = 100, message = "Cursor cannot exceed 100 characters")
    @Schema(description = "Opaque cursor: the nextCursor of the previous page")
    private String cursor;

    public int getLimit() {
        return (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
    }
}
