package com.alqaseh.ecommerce.features.payment.dto.request;

import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "(\\d{12,19})?", message = "Card number must be 12 to 19 digits")
    private String cardNumber;

    // XYZ_WALLET
    @Schema(description = "XYZ Wallet phone number (required for XyzWallet)", example = "+9647800000000")
    @Pattern(regexp = "(\\+?\\d{8,15})?", message = "Phone number must be 8 to 15 digits with an optional leading +")
    private String phoneNumber;

    @Schema(description = "XYZ Wallet password (required for XyzWallet, never logged)", example = "walletSecret123")
    @Size(max = 128, message = "Wallet password cannot exceed 128 characters")
    private String walletPassword;

    /** Only the fields of the chosen method are accepted: a card number sent with a wallet payment (or the reverse) is a client bug. */
    @AssertTrue(message = "Send only the fields of the chosen payment method")
    private boolean isOnlyChosenMethodFields() {
        if (method == PaymentMethod.CREDIT_CARD) {
            return !StringUtils.hasText(phoneNumber) && !StringUtils.hasText(walletPassword);
        }
        if (method == PaymentMethod.XYZ_WALLET) {
            return !StringUtils.hasText(cardNumber);
        }
        return true;
    }

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
