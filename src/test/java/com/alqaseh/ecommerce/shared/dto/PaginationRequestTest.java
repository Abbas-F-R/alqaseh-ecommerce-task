package com.alqaseh.ecommerce.shared.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationRequestTest {

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
    @DisplayName("Defaults are page 0, size 10 and valid")
    void defaultsAreValid() {
        PaginationRequest request = new PaginationRequest();

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("Negative page is rejected")
    void negativePageIsRejected() {
        PaginationRequest request = PaginationRequest.builder().page(-1).build();

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString()).containsExactly("page");
    }

    @Test
    @DisplayName("Size below 1 or above the maximum is rejected")
    void sizeOutOfRangeIsRejected() {
        assertThat(validator.validate(PaginationRequest.builder().size(0).build()))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("size");
        assertThat(validator.validate(PaginationRequest.builder().size(PaginationRequest.MAX_SIZE + 1).build()))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("size");
        assertThat(validator.validate(PaginationRequest.builder().size(PaginationRequest.MAX_SIZE).build())).isEmpty();
    }

    @Test
    @DisplayName("Pageable carries page, size and the endpoint-chosen sort")
    void pageableUsesEndpointSort() {
        Pageable pageable = PaginationRequest.builder().page(2).size(5).build()
                .toPageable(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
