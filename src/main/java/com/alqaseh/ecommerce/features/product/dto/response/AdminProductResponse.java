package com.alqaseh.ecommerce.features.product.dto.response;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed product response for administrators, including cost, exact stock, and audit tracking")
public class AdminProductResponse {

    private UUID id;
    private String name;
    private ProductCategory category;
    private BigDecimal price;
    private BigDecimal cost;
    private Integer availableQuantity;
    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;
}
