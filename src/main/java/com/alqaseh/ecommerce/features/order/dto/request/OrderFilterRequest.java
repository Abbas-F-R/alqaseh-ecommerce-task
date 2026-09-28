package com.alqaseh.ecommerce.features.order.dto.request;

import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Criteria parameters for searching and filtering customer orders")
public class OrderFilterRequest extends PaginationRequest {

    @Schema(description = "Username contains")
    private String customer;

    @Schema(description = "Customer id")
    private UUID customerId;

    @Schema(description = "Payment method (any letter case)")
    private PaymentMethod paymentMethod;
}
