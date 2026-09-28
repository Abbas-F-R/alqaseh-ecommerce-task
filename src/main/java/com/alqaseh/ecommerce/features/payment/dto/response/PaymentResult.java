package com.alqaseh.ecommerce.features.payment.dto.response;

/** Outcome of a payment attempt. A decline is a normal business outcome, not an exception. */
public record PaymentResult(boolean successful, String transactionId, String message) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId, "Payment processed successfully");
    }

    public static PaymentResult failure(String message) {
        return new PaymentResult(false, null, message);
    }
}
