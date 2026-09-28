package com.alqaseh.ecommerce.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Envelope of every successful response")
public record ApiResponse<T>(
        @Schema(description = "When the response was produced (UTC)") Instant timestamp,
        @Schema(description = "Always true for successful responses") boolean success,
        @Schema(description = "The payload") T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Instant.now(), true, data);
    }
}
