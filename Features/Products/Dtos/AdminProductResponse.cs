using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Product as seen by admins: cost, exact available quantity and who created / last updated it and when.
/// </summary>
public class AdminProductResponse
{
    [Sqid]
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;

    /// <summary>furniture, electronics, beauty or garden.</summary>
    public string Category { get; set; } = string.Empty;
    public decimal Price { get; set; }
    public decimal Cost { get; set; }
    public int AvailableQuantity { get; set; }

    [Sqid]
    public long CreatedBy { get; set; }
    public DateTime CreatedAt { get; set; }

    /// <summary>Null until the product is updated for the first time.</summary>
    [Sqid]
    public long? UpdatedBy { get; set; }
    public DateTime? UpdatedAt { get; set; }
}
