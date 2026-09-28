package com.alqaseh.ecommerce.features.product.dto.request;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Criteria parameters for searching and filtering products")
public class ProductFilterRequest extends PaginationRequest {

    @Schema(description = "Name contains")
    private String name;

    @Schema(description = "Product category (any letter case)")
    private ProductCategory category;
}
