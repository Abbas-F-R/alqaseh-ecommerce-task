using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

public class OrderItemRequest
{
    /// <summary>Product id as returned by the product list.</summary>
    [Sqid]
    public long ProductId { get; set; }

    /// <example>2</example>
    public int Quantity { get; set; }
}
