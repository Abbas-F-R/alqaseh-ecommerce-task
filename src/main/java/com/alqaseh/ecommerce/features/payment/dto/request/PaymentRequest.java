package com.alqaseh.ecommerce.features.payment.dto.request;

import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * Payment method plus the credentials that method needs. The "which fields are required for which method"
 * rule is request-shape validation, so it lives here and runs before the service is entered.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment method and its credentials")
public class PaymentRequest {

    @NotNull(message = "Payment method is required")
    @Schema(description = "Selected payment method", example = "CreditCard")
    private PaymentMethod method;

    // CREDIT_CARD
    @Schema(description = "Credit card number (required for CreditCard)", example = "4111111111111111")
    private String cardNumber;

    // XYZ_WALLET
    @Schema(description = "XYZ Wallet phone number (required for XyzWallet)", example = "+9647800000000")
    private String phoneNumber;

    @Schema(description = "XYZ Wallet password (required for XyzWallet, never logged)", example = "walletSecret123")
    private String walletPassword;

    @AssertTrue(message = "Card number is required for CreditCard payments")
    private boolean isCardNumberProvided() {
        return method != PaymentMethod.CREDIT_CARD || StringUtils.hasText(cardNumber);
    }

    @AssertTrue(message = "Phone number and wallet password are required for XyzWallet payments")
    private boolean isWalletCredentialsProvided() {
        return method != PaymentMethod.XYZ_WALLET
                || (StringUtils.hasText(phoneNumber) && StringUtils.hasText(walletPassword));
    }
}
