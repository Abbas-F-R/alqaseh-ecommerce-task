using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// Admin order list filters and pagination.
/// </summary>
public class OrderFilter : BaseFilter
{
    /// <summary>Orders of customers whose username contains this text (case-insensitive).</summary>
    public string? Customer { get; set; }

    /// <summary>Orders of this customer (the customer's id).</summary>
    [Sqid]
    public long? CustomerId { get; set; }

    /// <summary>CreditCard or XyzWallet (any letter case).</summary>
    public string? PaymentMethod { get; set; }
}
