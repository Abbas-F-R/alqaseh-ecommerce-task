package com.alqaseh.ecommerce.features.payment.processor;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated XYZ Wallet gateway: the password {@code wrongPassword} (or the phone number {@code FAIL}) is
 * always rejected. The wallet password and phone number are never logged.
 */
@Component
public class XyzWalletPaymentProcessor implements PaymentProcessor {

    private static final String WRONG_PASSWORD_TRIGGER = "wrongPassword";
    private static final String FAILURE_TRIGGER = "FAIL";

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.XYZ_WALLET;
    }

    @Override
    public PaymentResult process(BigDecimal amount, PaymentRequest request) {
        if (WRONG_PASSWORD_TRIGGER.equals(request.getWalletPassword().trim())
                || FAILURE_TRIGGER.equalsIgnoreCase(request.getPhoneNumber().trim())) {
            return PaymentResult.failure("Invalid wallet credentials or phone number");
        }
        return PaymentResult.success("XYZ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
