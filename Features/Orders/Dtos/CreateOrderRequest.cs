namespace AlQaseh_Ecommerce_API.Features.Orders.Dtos;

/// <summary>
/// An order: one or more products with quantities, an optional discount code and the payment method.
/// </summary>
public class CreateOrderRequest
{
    public List<OrderItemRequest> Items { get; set; } = new();

    /// <summary>At most one discount code per order.</summary>
    /// <example>ABC123</example>
    public string? DiscountCode { get; set; }

    public PaymentRequest Payment { get; set; } = new();
}
