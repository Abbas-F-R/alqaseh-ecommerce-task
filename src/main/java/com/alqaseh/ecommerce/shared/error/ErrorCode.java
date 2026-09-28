package com.alqaseh.ecommerce.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable, translatable error codes. Business logic only knows the enum constant;
 * {@code messageKey} is resolved to a localized message at the HTTP boundary.
 */
@Getter
public enum ErrorCode {

    // Product
    PRODUCT_NOT_FOUND("product.not_found", HttpStatus.NOT_FOUND),
    PRODUCT_NAME_ALREADY_EXISTS("product.name_exists", HttpStatus.CONFLICT),

    // Order & stock
    INSUFFICIENT_STOCK("order.insufficient_stock", HttpStatus.BAD_REQUEST),
    CONCURRENT_MODIFICATION("common.concurrent_modification", HttpStatus.CONFLICT),

    // Discount
    DISCOUNT_NOT_FOUND("discount.not_found", HttpStatus.BAD_REQUEST),
    DISCOUNT_EXPIRED("discount.expired", HttpStatus.BAD_REQUEST),
    DISCOUNT_ALREADY_USED("discount.already_used", HttpStatus.BAD_REQUEST),
    MINIMUM_ORDER_TOTAL_NOT_MET("discount.minimum_total_not_met", HttpStatus.BAD_REQUEST),
    DISCOUNT_EXCEEDS_TOTAL("discount.exceeds_total", HttpStatus.BAD_REQUEST),

    // Payment
    PAYMENT_FAILED("payment.failed", HttpStatus.PAYMENT_REQUIRED),
    PAYMENT_METHOD_NOT_SUPPORTED("payment.method_not_supported", HttpStatus.BAD_REQUEST),

    // Auth & general
    INVALID_CREDENTIALS("auth.invalid_credentials", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("auth.unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("common.forbidden", HttpStatus.FORBIDDEN),
    BAD_REQUEST("common.bad_request", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("validation.failed", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("common.internal_error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String messageKey;
    private final HttpStatus httpStatus;

    ErrorCode(String messageKey, HttpStatus httpStatus) {
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }
}
