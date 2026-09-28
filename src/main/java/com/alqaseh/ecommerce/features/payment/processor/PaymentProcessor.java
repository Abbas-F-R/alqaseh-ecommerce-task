package com.alqaseh.ecommerce.features.payment.processor;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;

import java.math.BigDecimal;

/** Strategy for one payment method. Add a new method by adding an enum value and one implementation. */
public interface PaymentProcessor {

    PaymentMethod getSupportedMethod();

    PaymentResult process(BigDecimal amount, PaymentRequest request);
}
