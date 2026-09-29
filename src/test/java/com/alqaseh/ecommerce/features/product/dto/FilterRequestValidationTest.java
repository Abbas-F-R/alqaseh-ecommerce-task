package com.alqaseh.ecommerce.features.product.dto;

import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.util.ProductCursor;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** List filters and pagination are bounded, so a request cannot make the database work on an absurd value. */
class FilterRequestValidationTest {

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

    @Test
    @DisplayName("Pagination: page 0 to 100,000 and size 1 to 50")
    void pagination() {
        assertThat(validator.validate(PaginationRequest.builder().page(0).size(1).build())).isEmpty();
        assertThat(validator.validate(PaginationRequest.builder().page(PaginationRequest.MAX_PAGE).size(50).build())).isEmpty();
        assertThat(validator.validate(PaginationRequest.builder().page(PaginationRequest.MAX_PAGE + 1).build())).isNotEmpty();
        assertThat(validator.validate(PaginationRequest.builder().page(Integer.MAX_VALUE).build())).isNotEmpty();
        assertThat(validator.validate(PaginationRequest.builder().page(-1).build())).isNotEmpty();
        assertThat(validator.validate(PaginationRequest.builder().size(0).build())).isNotEmpty();
        assertThat(validator.validate(PaginationRequest.builder().size(51).build())).isNotEmpty();
    }

    @Test
    @DisplayName("Product name filter: 150 characters at most")
    void productNameFilter() {
        assertThat(validator.validate(AdminProductFilterRequest.builder().name("a".repeat(150)).build())).isEmpty();
        assertThat(validator.validate(AdminProductFilterRequest.builder().name("a".repeat(151)).build())).isNotEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().name("a".repeat(151)).build())).isNotEmpty();
    }

    @Test
    @DisplayName("Customer filter of the order list: 50 characters at most")
    void customerFilter() {
        assertThat(validator.validate(OrderFilterRequest.builder().customer("a".repeat(50)).build())).isEmpty();
        assertThat(validator.validate(OrderFilterRequest.builder().customer("a".repeat(51)).build())).isNotEmpty();
    }

    @Test
    @DisplayName("Cursor: a real cursor is valid; garbage, an over-long value and a non-UUID payload are not")
    void cursor() {
        assertThat(validator.validate(CustomerProductFilterRequest.builder().cursor(ProductCursor.encode(UUID.randomUUID())).build())).isEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().cursor("not-a-cursor").build())).isNotEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().cursor("A".repeat(101)).build())).isNotEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().cursor("A".repeat(100_000)).build())).isNotEmpty();
    }

    @Test
    @DisplayName("Limit of the customer list: 1 to 50")
    void limit() {
        assertThat(validator.validate(CustomerProductFilterRequest.builder().limit(1).build())).isEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().limit(50).build())).isEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().limit(0).build())).isNotEmpty();
        assertThat(validator.validate(CustomerProductFilterRequest.builder().limit(51).build())).isNotEmpty();
    }
}
