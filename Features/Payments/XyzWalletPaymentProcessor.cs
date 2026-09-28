using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Payments;

/// <summary>
/// Fake XyzWallet gateway: needs the customer's phone number and wallet password; every wallet is accepted
/// except when the password is <see cref="WrongPassword"/>, which is always rejected (so that the failure path can be exercised).
/// </summary>
[Scoped]
public class XyzWalletPaymentProcessor : IPaymentProcessor
{
    public const string WrongPassword = "wrong-password";

    public string Method => PaymentMethods.XyzWallet;

    public PaymentResult Charge(decimal amount, PaymentRequest payment)
    {
        if (string.Equals(payment.WalletPassword, WrongPassword, StringComparison.Ordinal))
            return new PaymentResult(false, Error: "Wallet password rejected.");

        return new PaymentResult(true, "XYZ-" + Guid.NewGuid().ToString("N")[..12].ToUpperInvariant());
    }
}
