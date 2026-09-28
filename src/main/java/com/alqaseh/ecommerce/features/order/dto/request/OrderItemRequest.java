package com.alqaseh.ecommerce.features.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product and quantity item in an order request")
public class OrderItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "ID of the product to purchase", example = "01923450-0000-7000-8000-000000000003")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Quantity to purchase", example = "2")
    private Integer quantity;
}
