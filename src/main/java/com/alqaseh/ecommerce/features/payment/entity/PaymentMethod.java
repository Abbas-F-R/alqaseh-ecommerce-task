package com.alqaseh.ecommerce.features.payment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

/**
 * The two payment methods. The API spells them {@code CreditCard} and {@code XyzWallet}; input is accepted in any letter case
 * (also {@code credit_card}). The database stores the constant name.
 */
public enum PaymentMethod {
    CREDIT_CARD("CreditCard"),
    XYZ_WALLET("XyzWallet");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PaymentMethod from(String text) {
        String wanted = normalize(text);
        return Arrays.stream(values())
                .filter(m -> normalize(m.value).equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + text));
    }

    private static String normalize(String text) {
        return text.trim().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
