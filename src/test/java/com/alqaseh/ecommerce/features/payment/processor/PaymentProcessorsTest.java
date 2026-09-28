package com.alqaseh.ecommerce.features.payment.processor;

import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProcessorsTest {

    private final CreditCardPaymentProcessor cardProcessor = new CreditCardPaymentProcessor();
    private final XyzWalletPaymentProcessor walletProcessor = new XyzWalletPaymentProcessor();

    private static PaymentRequest card(String number) {
        return PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber(number).build();
    }

    private static PaymentRequest wallet(String phone, String password) {
        return PaymentRequest.builder().method(PaymentMethod.XYZ_WALLET).phoneNumber(phone).walletPassword(password).build();
    }

    @Test
    @DisplayName("Credit card: a normal card is authorized")
    void cardAuthorized() {
        PaymentResult result = cardProcessor.process(BigDecimal.valueOf(100), card("4111222233334444"));

        assertThat(result.successful()).isTrue();
        assertThat(result.transactionId()).startsWith("CC-");
    }

    @Test
    @DisplayName("Credit card: the simulated declined card number is declined")
    void cardDeclined() {
        PaymentResult result = cardProcessor.process(BigDecimal.valueOf(100), card("0000000000000000"));

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("declined");
        assertThat(result.transactionId()).isNull();
    }

    @Test
    @DisplayName("XYZ wallet: valid credentials are authorized")
    void walletAuthorized() {
        PaymentResult result = walletProcessor.process(BigDecimal.valueOf(50), wallet("+9647801234567", "secretWalletPass"));

        assertThat(result.successful()).isTrue();
        assertThat(result.transactionId()).startsWith("XYZ-");
    }

    @Test
    @DisplayName("XYZ wallet: the simulated wrong password is rejected")
    void walletWrongPassword() {
        PaymentResult result = walletProcessor.process(BigDecimal.valueOf(50), wallet("+9647801234567", "wrongPassword"));

        assertThat(result.successful()).isFalse();
    }

    @Test
    @DisplayName("Factory returns the processor registered for a method and nothing for an unregistered one")
    void factoryLooksUpByMethod() {
        PaymentProcessorFactory factory = new PaymentProcessorFactory(List.of(cardProcessor));

        assertThat(factory.getProcessor(PaymentMethod.CREDIT_CARD)).containsSame(cardProcessor);
        assertThat(factory.getProcessor(PaymentMethod.XYZ_WALLET)).isEmpty();
    }
}
