using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Payments;

/// <summary>
/// Fake credit card gateway: every card is accepted except <see cref="DeclinedCardNumber"/>, which is always declined
/// (so that the failure path can be exercised).
/// </summary>
[Scoped]
public class CreditCardPaymentProcessor : IPaymentProcessor
{
    public const string DeclinedCardNumber = "4000000000000002";

    public string Method => PaymentMethods.CreditCard;

    public PaymentResult Charge(decimal amount, PaymentRequest payment)
    {
        if (string.Equals(payment.CardNumber?.Trim(), DeclinedCardNumber, StringComparison.Ordinal))
            return new PaymentResult(false, Error: "Card declined by the issuer.");

        return new PaymentResult(true, "CC-" + Guid.NewGuid().ToString("N")[..12].ToUpperInvariant());
    }
}
