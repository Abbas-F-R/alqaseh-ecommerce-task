namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// How the customer pays. Send the fields of the chosen method.
/// </summary>
public class PaymentRequest
{
    /// <summary>CreditCard or XyzWallet (any letter case).</summary>
    /// <example>CreditCard</example>
    public string Method { get; set; } = string.Empty;

    /// <summary>CreditCard: 12-19 digits.</summary>
    /// <example>4111111111111111</example>
    public string? CardNumber { get; set; }

    /// <summary>XyzWallet: the customer's phone number.</summary>
    /// <example>+9647701234567</example>
    public string? PhoneNumber { get; set; }

    /// <summary>XyzWallet: the wallet password.</summary>
    public string? WalletPassword { get; set; }
}
