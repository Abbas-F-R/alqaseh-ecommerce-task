package com.alqaseh.ecommerce.features.order.dto.response;

import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer order history view")
public class CustomerOrderResponse {

    private UUID id;
    private BigDecimal totalPrice;
    private PaymentMethod paymentMethod;
    private Instant purchaseDate;
    private BigDecimal discountAmount;
    private List<OrderItemResponse> items;
}
