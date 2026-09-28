using AlQaseh_Ecommerce_API.Features.Orders.Dtos;

namespace AlQaseh_Ecommerce_API.Features.Payments;

public record PaymentResult(bool Success, string? TransactionId = null, string? Error = null);

/// <summary>
/// A payment method. The implementations are fake: no real provider is called.
/// </summary>
public interface IPaymentProcessor
{
    /// <summary>The payment method this processor handles (see <see cref="PaymentMethods"/>).</summary>
    string Method { get; }

    PaymentResult Charge(decimal amount, PaymentRequest payment);
}
