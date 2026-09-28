package com.alqaseh.ecommerce.shared.exception;

import com.alqaseh.ecommerce.shared.localization.LocalizationService;
import com.alqaseh.ecommerce.shared.response.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");

    private GlobalExceptionHandler handler(boolean debug) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return new GlobalExceptionHandler(new LocalizationService(messages), debug);
    }

    @Test
    @DisplayName("Unique-index violation on the product name (a race the pre-check could not see) becomes 409, not 500")
    void duplicateProductNameRaceIsConflict() {
        var ex = new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uq_products_name\""));

        ResponseEntity<ApiErrorResponse> response = handler(false).handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_NAME_ALREADY_EXISTS");
        assertThat(response.getBody().message()).isEqualTo("Product with this name already exists");
    }

    @Test
    @DisplayName("Any other integrity violation is a bug: 500 with a generic body")
    void unknownIntegrityViolationIsServerError() {
        var ex = new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: null value in column \"name\" violates not-null constraint"));

        ResponseEntity<ApiErrorResponse> response = handler(false).handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("null value", "not-null");
        assertThat(response.getBody().debug()).isNull();
    }

    @Test
    @DisplayName("Optimistic-lock failure (concurrent stock/discount update): 409 CONCURRENT_MODIFICATION")
    void optimisticLockIsConflict() {
        ResponseEntity<ApiErrorResponse> response =
                handler(false).handleOptimisticLocking(new OptimisticLockingFailureException("stale"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("Debug details are only included when explicitly enabled (development)")
    void debugDetailsOnlyWhenEnabled() {
        var boom = new IllegalStateException("kaboom");

        assertThat(handler(false).handleUnexpected(boom, request).getBody().debug()).isNull();
        assertThat(handler(true).handleUnexpected(boom, request).getBody().debug())
                .contains("IllegalStateException").contains("kaboom");
    }
}
