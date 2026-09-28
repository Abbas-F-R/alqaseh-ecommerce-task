package com.alqaseh.ecommerce.features.payment.processor;

import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Picks the {@link PaymentProcessor} for a payment method from all processors found in the context. */
@Component
public class PaymentProcessorFactory {

    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    public PaymentProcessorFactory(List<PaymentProcessor> processorList) {
        processorList.forEach(processor -> processors.put(processor.getSupportedMethod(), processor));
    }

    public Optional<PaymentProcessor> getProcessor(PaymentMethod method) {
        return Optional.ofNullable(processors.get(method));
    }
}
