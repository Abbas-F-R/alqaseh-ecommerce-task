package com.alqaseh.ecommerce.features.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
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

    /** Per line. With at most 100 lines an order holds at most 1,000,000 units, so its amounts always fit the money columns. */
    public static final int MAX_QUANTITY = 10_000;

    @NotNull(message = "Product ID is required")
    @Schema(description = "ID of the product to purchase", example = "01923450-0000-7000-8000-000000000003")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = MAX_QUANTITY, message = "Quantity cannot exceed " + MAX_QUANTITY)
    @Schema(description = "Quantity to purchase (1-10000)", example = "2", minimum = "1", maximum = "10000")
    private Integer quantity;
}
