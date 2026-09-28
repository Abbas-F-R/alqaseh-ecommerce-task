namespace AlQaseh_Ecommerce_API.Features.Discounts;

/// <summary>
/// A fixed-amount discount code (internal, never returned by the API).
/// </summary>
public class DiscountCodeDto
{
    public long Id { get; set; }
    public string Code { get; set; } = string.Empty;

    /// <summary>Amount subtracted from the order total.</summary>
    public decimal Amount { get; set; }

    /// <summary>Smallest order subtotal the code can be used on.</summary>
    public decimal MinimumOrderTotal { get; set; }
    public DateTime ExpiresAt { get; set; }
    public bool Used { get; set; }
}
