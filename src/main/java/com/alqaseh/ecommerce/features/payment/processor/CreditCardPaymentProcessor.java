package com.alqaseh.ecommerce.features.payment.processor;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Simulated card gateway: the card number {@code 0000000000000000} (or {@code FAIL}) is always declined. */
@Component
public class CreditCardPaymentProcessor implements PaymentProcessor {

    private static final String DECLINED_CARD_NUMBER = "0000000000000000";
    private static final String FAILURE_TRIGGER = "FAIL";

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentResult process(BigDecimal amount, PaymentRequest request) {
        String card = request.getCardNumber().trim();
        if (DECLINED_CARD_NUMBER.equals(card) || FAILURE_TRIGGER.equalsIgnoreCase(card)) {
            return PaymentResult.failure("Transaction declined by card issuer");
        }
        return PaymentResult.success("CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
