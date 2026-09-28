package com.alqaseh.ecommerce.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Page/size part of every list request. Bounds are enforced by Bean Validation before the service runs;
 * the sort order is chosen by each endpoint (never by the client), so arbitrary sort properties cannot reach JPA.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    @Min(value = 0, message = "Page number cannot be negative")
    @Schema(description = "Page (from 0)", defaultValue = "0", minimum = "0")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = MAX_SIZE, message = "Page size cannot exceed " + MAX_SIZE)
    @Schema(description = "Page size", defaultValue = "10", minimum = "1", maximum = "50")
    @Builder.Default
    private int size = DEFAULT_SIZE;

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
