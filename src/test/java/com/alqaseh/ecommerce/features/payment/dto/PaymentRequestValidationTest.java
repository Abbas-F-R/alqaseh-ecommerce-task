package com.alqaseh.ecommerce.features.payment.dto;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** "Which payment fields are required for which method" is request validation, checked before any service runs. */
class PaymentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Method is required")
    void methodIsRequired() {
        assertThat(validator.validate(new PaymentRequest())).extracting(v -> v.getPropertyPath().toString()).contains("method");
    }

    @Test
    @DisplayName("CREDIT_CARD needs a card number")
    void cardNeedsCardNumber() {
        PaymentRequest missing = PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).build();
        PaymentRequest blank = PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("  ").build();
        PaymentRequest ok = PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111222233334444").build();

        assertThat(validator.validate(missing)).hasSize(1);
        assertThat(validator.validate(blank)).hasSize(1);
        assertThat(validator.validate(ok)).isEmpty();
    }

    @Test
    @DisplayName("XYZ_WALLET needs phone number and wallet password, and no card number")
    void walletNeedsPhoneAndPassword() {
        PaymentRequest noPassword = PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET).phoneNumber("+9647800000000").build();
        PaymentRequest noPhone = PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET).walletPassword("secret").build();
        PaymentRequest ok = PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET)
                .phoneNumber("+9647800000000").walletPassword("secret").build();

        assertThat(validator.validate(noPassword)).hasSize(1);
        assertThat(validator.validate(noPhone)).hasSize(1);
        assertThat(validator.validate(ok)).isEmpty();
    }
}
