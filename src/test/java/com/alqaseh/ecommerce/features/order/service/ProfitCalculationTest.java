package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.order.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProfitCalculationTest {

    @Test
    @DisplayName("Verify Order profit calculation: Revenue after discount - total cost")
    void shouldCalculateProfitCorrectly() {
        Order order = Order.builder()
                .subtotalAmount(BigDecimal.valueOf(30000))
                .discountAmount(BigDecimal.valueOf(5000))
                .totalAmount(BigDecimal.valueOf(25000))
                .totalCost(BigDecimal.valueOf(20000))
                .build();

        // Profit = TotalAmount (25,000) - TotalCost (20,000) = 5,000
        assertThat(order.getProfit()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("Order with zero discount computes profit as subtotal - total cost")
    void shouldCalculateProfitWithoutDiscount() {
        Order order = Order.builder()
                .subtotalAmount(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(15000))
                .totalCost(BigDecimal.valueOf(9000))
                .build();

        assertThat(order.getProfit()).isEqualByComparingTo(BigDecimal.valueOf(6000));
    }
}
