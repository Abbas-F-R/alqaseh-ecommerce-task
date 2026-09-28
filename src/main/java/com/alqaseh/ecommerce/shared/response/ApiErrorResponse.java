package com.alqaseh.ecommerce.shared.response;

import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body. {@code debug} is only populated when
 * {@code application.error.include-debug-details=true} (development profile).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error body of every failed request")
public record ApiErrorResponse(
        @Schema(description = "When the error happened (UTC)") Instant timestamp,
        @Schema(description = "HTTP status code", example = "400") int status,
        @Schema(description = "Stable error code, safe to program against (see ErrorCode)", example = "VALIDATION_ERROR") String code,
        @Schema(description = "Human readable message in the language of the Accept-Language header (en, ar)") String message,
        @Schema(description = "Request path", example = "/api/products") String path,
        @Schema(description = "Only for VALIDATION_ERROR: message per invalid field", example = "{\"price\": \"Price must be greater than or equal to 0\"}")
        Map<String, String> validationErrors,
        @Schema(description = "Only in the dev profile, for 500 errors: exception class and message", nullable = true) String debug
) {

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ApiErrorResponse(Instant.now(), errorCode.getHttpStatus().value(), errorCode.name(), message, path, null, null);
    }

    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, null, null);
    }

    public ApiErrorResponse withValidationErrors(Map<String, String> errors) {
        return new ApiErrorResponse(timestamp, status, code, message, path, errors, debug);
    }

    public ApiErrorResponse withDebug(String debug) {
        return new ApiErrorResponse(timestamp, status, code, message, path, validationErrors, debug);
    }
}
