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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Length and format of the payment fields, and "only the fields of the chosen method": card data and wallet data never mix. */
class PaymentFieldsValidationTest {

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

    private static boolean card(String number) {
        return validator.validate(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber(number).build()).isEmpty();
    }

    private static boolean wallet(String phone, String password) {
        return validator.validate(PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET).phoneNumber(phone).walletPassword(password).build()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"411111111111", "4111111111111111", "4111111111111111111"})
    @DisplayName("A card number of 12, 16 and 19 digits is valid")
    void validCards(String number) {
        assertThat(card(number)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"41111111111", "41111111111111111111", "4111 1111 1111 1111", "abcd1111abcd1111", "4111-1111-1111-1111", "-4111111111111111"})
    @DisplayName("A card number that is too short, too long or not only digits is rejected")
    void invalidCards(String number) {
        assertThat(card(number)).isFalse();
    }

    @Test
    @DisplayName("A gigantic card number is rejected, not processed")
    void gigantic() {
        assertThat(card("4".repeat(1_000_000))).isFalse();
    }

    @Test
    @DisplayName("Wallet: phone of 8 to 15 digits with an optional +, and a password of 1 to 128 characters")
    void wallets() {
        assertThat(wallet("+9647800000000", "secret")).isTrue();
        assertThat(wallet("07800000", "s")).isTrue();
        assertThat(wallet("+964780000000000", "p".repeat(128))).isTrue();

        assertThat(wallet("1234567", "secret")).isFalse();          // 7 digits
        assertThat(wallet("+9647800000000000", "secret")).isFalse(); // 16 digits
        assertThat(wallet("+96478abc0000", "secret")).isFalse();
        assertThat(wallet("+964 780 000 0000", "secret")).isFalse();
        assertThat(wallet("+9647800000000", "p".repeat(129))).isFalse();
        assertThat(wallet("+9647800000000", "")).isFalse();
        assertThat(wallet(null, "secret")).isFalse();
    }

    @Test
    @DisplayName("Card data with a wallet payment, or wallet data with a card payment, is rejected; an empty unused field is tolerated")
    void onlyTheChosenMethodsFields() {
        assertThat(validator.validate(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111111111111111")
                .phoneNumber("+9647800000000").build())).isNotEmpty();
        assertThat(validator.validate(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111111111111111")
                .walletPassword("secret").build())).isNotEmpty();
        assertThat(validator.validate(PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET).phoneNumber("+9647800000000")
                .walletPassword("secret").cardNumber("4111111111111111").build())).isNotEmpty();

        assertThat(validator.validate(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111111111111111")
                .phoneNumber("").walletPassword("").build())).isEmpty();
    }
}
