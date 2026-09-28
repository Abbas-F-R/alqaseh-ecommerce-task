package com.alqaseh.ecommerce.features.product.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StockStatusTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("Boundary testing: Quantities 0 to 4 must resolve to LOW")
    void shouldResolveToLow(int quantity) {
        assertThat(StockStatus.fromQuantity(quantity)).isEqualTo(StockStatus.LOW);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 8, 9})
    @DisplayName("Boundary testing: Quantities 5 to 9 must resolve to LIMITED")
    void shouldResolveToLimited(int quantity) {
        assertThat(StockStatus.fromQuantity(quantity)).isEqualTo(StockStatus.LIMITED);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 25, 100, 1000})
    @DisplayName("Boundary testing: Quantities 10 and above must resolve to AVAILABLE")
    void shouldResolveToAvailable(int quantity) {
        assertThat(StockStatus.fromQuantity(quantity)).isEqualTo(StockStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Edge case: quantity exactly at boundary 4 is LOW, 5 is LIMITED, 9 is LIMITED, 10 is AVAILABLE")
    void shouldVerifyExactBoundaries() {
        assertThat(StockStatus.fromQuantity(4)).isEqualTo(StockStatus.LOW);
        assertThat(StockStatus.fromQuantity(5)).isEqualTo(StockStatus.LIMITED);
        assertThat(StockStatus.fromQuantity(9)).isEqualTo(StockStatus.LIMITED);
        assertThat(StockStatus.fromQuantity(10)).isEqualTo(StockStatus.AVAILABLE);
    }
}
