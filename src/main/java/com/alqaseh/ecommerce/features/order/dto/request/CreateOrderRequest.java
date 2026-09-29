package com.alqaseh.ecommerce.features.order.dto.request;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for submitting a new order")
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Size(max = 100, message = "Order cannot contain more than 100 items")
    @Valid
    @Schema(description = "List of products and quantities")
    private List<@NotNull(message = "An order line cannot be null") @Valid OrderItemRequest> items;

    @Size(max = 50, message = "Discount code must not exceed 50 characters")
    @Pattern(regexp = "\\P{Cc}*", message = "Discount code must not contain control characters")
    @Schema(description = "Optional promotional discount code", example = "WELCOME10")
    private String discountCode;

    @NotNull(message = "Payment details are required")
    @Valid
    @Schema(description = "Payment method and credentials")
    private PaymentRequest payment;
}
