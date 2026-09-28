namespace AlQaseh_Ecommerce_API.Features.Payments;

/// <summary>
/// The two supported payment methods. They are stored and returned as written here; input is accepted in any letter case.
/// </summary>
public static class PaymentMethods
{
    public const string CreditCard = "CreditCard";
    public const string XyzWallet = "XyzWallet";

    public static readonly IReadOnlyList<string> All = [CreditCard, XyzWallet];

    /// <summary>The canonical spelling of a payment method, or null when the value is not supported.</summary>
    public static string? Normalize(string? value) =>
        All.FirstOrDefault(method => string.Equals(method, value?.Trim(), StringComparison.OrdinalIgnoreCase));
}
