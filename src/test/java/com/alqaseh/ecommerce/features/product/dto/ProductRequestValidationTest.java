package com.alqaseh.ecommerce.features.product.dto;

import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundaries of every product field: null, blank, too long, negative, zero where invalid, the maximum and one above it. */
class ProductRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ProductRequest product(String name, String price, String cost, Integer quantity) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setCategory(ProductCategory.FURNITURE);
        request.setPrice(price == null ? null : new BigDecimal(price));
        request.setCost(cost == null ? null : new BigDecimal(cost));
        request.setAvailableQuantity(quantity);
        return request;
    }

    private static boolean valid(ProductRequest request) {
        return validator.validate(request).isEmpty();
    }

    @Test
    @DisplayName("Name: 150 characters are valid, 151 are not; null, empty and blank are rejected; surrounding spaces are trimmed")
    void name() {
        assertThat(valid(product("a".repeat(150), "10", "5", 1))).isTrue();
        assertThat(valid(product("a".repeat(151), "10", "5", 1))).isFalse();
        assertThat(valid(product(null, "10", "5", 1))).isFalse();
        assertThat(valid(product("", "10", "5", 1))).isFalse();
        assertThat(valid(product("   ", "10", "5", 1))).isFalse();
        assertThat(product("  Chair  ", "10", "5", 1).getName()).isEqualTo("Chair");
    }

    @Test
    @DisplayName("Price: greater than 0, at most 10 digits before and 2 after the decimal point")
    void price() {
        assertThat(valid(product("A", "0.01", "0", 1))).isTrue();
        assertThat(valid(product("A", "9999999999.99", "0", 1))).isTrue();
        assertThat(valid(product("A", "0", "0", 1))).isFalse();
        assertThat(valid(product("A", "-1", "0", 1))).isFalse();
        assertThat(valid(product("A", "0.001", "0", 1))).isFalse();
        assertThat(valid(product("A", "10.005", "0", 1))).isFalse();
        assertThat(valid(product("A", "10000000000", "0", 1))).isFalse();
        assertThat(valid(product("A", null, "0", 1))).isFalse();
    }

    @Test
    @DisplayName("Cost: 0 or more, same precision, never above the price")
    void cost() {
        assertThat(valid(product("A", "10", "0", 1))).isTrue();
        assertThat(valid(product("A", "9999999999.99", "9999999999.99", 1))).isTrue();
        assertThat(valid(product("A", "10", "10", 1))).isTrue();
        assertThat(valid(product("A", "10", "10.01", 1))).isFalse();
        assertThat(valid(product("A", "50", "140", 1))).isFalse();
        assertThat(valid(product("A", "0.01", "0.02", 1))).isFalse();
        assertThat(valid(product("A", "10", "-0.01", 1))).isFalse();
        assertThat(valid(product("A", "10", "5.555", 1))).isFalse();
        assertThat(valid(product("A", "10", "10000000000", 1))).isFalse();
        assertThat(valid(product("A", "10", null, 1))).isFalse();
    }

    @Test
    @DisplayName("Cost above the price is one clear violation; a missing price or cost is reported by its own rule only")
    void costAbovePriceIsReportedOnce() {
        assertThat(validator.validate(product("A", "10", "11", 1)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("costNotAbovePrice");
        assertThat(validator.validate(product("A", null, "11", 1)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("price");
        assertThat(validator.validate(product("A", "10", null, 1)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("cost");
    }

    @Test
    @DisplayName("Quantity: 0 to 1,000,000")
    void quantity() {
        assertThat(valid(product("A", "10", "5", 0))).isTrue();
        assertThat(valid(product("A", "10", "5", ProductRequest.MAX_QUANTITY))).isTrue();
        assertThat(valid(product("A", "10", "5", ProductRequest.MAX_QUANTITY + 1))).isFalse();
        assertThat(valid(product("A", "10", "5", -1))).isFalse();
        assertThat(valid(product("A", "10", "5", Integer.MAX_VALUE))).isFalse();
        assertThat(valid(product("A", "10", "5", null))).isFalse();
    }

    @Test
    @DisplayName("Category is required")
    void category() {
        ProductRequest request = product("A", "10", "5", 1);
        request.setCategory(null);
        assertThat(valid(request)).isFalse();
    }
}
