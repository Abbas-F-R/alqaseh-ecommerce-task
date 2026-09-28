package com.alqaseh.ecommerce.features.product.dto.request;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Full product payload, used for both create (POST) and replace (PUT). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product data for create and update")
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 150, message = "Product name must not exceed 150 characters")
    @Schema(description = "Unique product name", example = "Standing Desk")
    private String name;

    @NotNull(message = "Product category is required")
    @Schema(description = "Product category", example = "furniture")
    private ProductCategory category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0")
    @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer and 2 fraction digits")
    @Schema(description = "Customer selling price", example = "350.00")
    private BigDecimal price;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.00", message = "Cost must be greater than or equal to 0")
    @Digits(integer = 10, fraction = 2, message = "Cost must have at most 10 integer and 2 fraction digits")
    @Schema(description = "Internal merchant cost", example = "220.00")
    private BigDecimal cost;

    @NotNull(message = "Available quantity is required")
    @Min(value = 0, message = "Available quantity must be greater than or equal to 0")
    @Schema(description = "Stock inventory count", example = "15")
    private Integer availableQuantity;

    /** Names are compared and stored trimmed, so whitespace can never create a "different" duplicate. */
    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}
