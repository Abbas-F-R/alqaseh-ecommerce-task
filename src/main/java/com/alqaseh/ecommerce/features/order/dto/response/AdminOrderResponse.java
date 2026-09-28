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
@Schema(description = "Detailed order view for administrators including total cost and net profit")
public class AdminOrderResponse {

    private UUID id;
    private UUID customerId;
    private String customerUsername;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;
    private BigDecimal profit;
    private PaymentMethod paymentMethod;
    private Instant purchaseDate;
    private List<OrderItemResponse> items;
}
