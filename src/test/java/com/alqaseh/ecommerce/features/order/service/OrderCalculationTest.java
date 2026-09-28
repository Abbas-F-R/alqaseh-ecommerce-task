package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.order.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCalculationTest {

    @Test
    @DisplayName("OrderItem line subtotal correctly calculates unitPrice * quantity")
    void shouldCalculateLineSubtotal() {
        OrderItem item = OrderItem.builder()
                .unitPrice(BigDecimal.valueOf(120.50))
                .unitCost(BigDecimal.valueOf(80.00))
                .quantity(3)
                .build();

        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(361.50));
        assertThat(item.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(240.00));
    }
}
