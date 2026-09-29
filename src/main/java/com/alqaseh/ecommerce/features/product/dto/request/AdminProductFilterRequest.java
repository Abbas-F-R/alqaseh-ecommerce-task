package com.alqaseh.ecommerce.features.product.dto.request;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import jakarta.validation.constraints.Size;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Admin product list: filters plus page/offset pagination (page from 0), so the dashboard can show totals and jump to any page. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Filters and page/offset pagination for the admin product list")
public class AdminProductFilterRequest extends PaginationRequest implements ProductCriteria {

    @Schema(description = "Name contains (case-insensitive)")
    @Size(max = 150, message = "Name filter cannot exceed 150 characters")
    private String name;

    @Schema(description = "Product category (any letter case)")
    private ProductCategory category;
}
