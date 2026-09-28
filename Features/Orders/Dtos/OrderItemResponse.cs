using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// An order line as it was at purchase time (name and price are snapshots).
/// </summary>
public class OrderItemResponse
{
    [Sqid]
    public long ProductId { get; set; }
    public string ProductName { get; set; } = string.Empty;
    public decimal UnitPrice { get; set; }
    public int Quantity { get; set; }
    public decimal Subtotal { get; set; }
}
