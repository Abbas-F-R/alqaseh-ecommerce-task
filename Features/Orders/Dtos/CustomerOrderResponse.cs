using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// An order as seen by its customer.
/// </summary>
public class CustomerOrderResponse
{
    [Sqid]
    public long Id { get; set; }

    /// <summary>Amount paid: items total minus the discount.</summary>
    public decimal TotalPrice { get; set; }

    /// <summary>CreditCard or XyzWallet.</summary>
    public string PaymentMethod { get; set; } = string.Empty;

    /// <summary>UTC time of the purchase.</summary>
    public DateTime PurchaseDate { get; set; }

    /// <summary>Amount taken off by the discount code; 0 when no code was used.</summary>
    public decimal DiscountAmount { get; set; }

    public List<OrderItemResponse> Items { get; set; } = new();
}
