using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// An order as seen by admins, including its profit.
/// </summary>
public class AdminOrderResponse
{
    [Sqid]
    public long Id { get; set; }

    [Sqid]
    public long CustomerId { get; set; }
    public string CustomerUsername { get; set; } = string.Empty;

    /// <summary>Items total before the discount.</summary>
    public decimal SubtotalAmount { get; set; }
    public decimal DiscountAmount { get; set; }

    /// <summary>Amount paid: subtotal minus the discount.</summary>
    public decimal TotalAmount { get; set; }

    /// <summary>What the items cost the shop.</summary>
    public decimal TotalCost { get; set; }

    /// <summary>Total profit of the order: TotalAmount minus TotalCost.</summary>
    public decimal Profit { get; set; }

    /// <summary>CreditCard or XyzWallet.</summary>
    public string PaymentMethod { get; set; } = string.Empty;

    /// <summary>UTC time of the purchase.</summary>
    public DateTime PurchaseDate { get; set; }

    public List<OrderItemResponse> Items { get; set; } = new();
}
