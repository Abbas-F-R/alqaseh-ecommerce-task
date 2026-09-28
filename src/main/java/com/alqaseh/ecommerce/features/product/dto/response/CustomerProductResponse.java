package com.alqaseh.ecommerce.features.product.dto.response;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.entity.StockStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer product response masking cost and displaying stock status category")
public class CustomerProductResponse {

    private UUID id;
    private String name;
    private ProductCategory category;
    private BigDecimal price;
    private StockStatus stockStatus;
}
