using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Product list filters and pagination.
/// </summary>
public class ProductFilter : BaseFilter
{
    /// <summary>Products whose name contains this text (case-insensitive).</summary>
    public string? Name { get; set; }

    /// <summary>furniture, electronics, beauty or garden (any letter case).</summary>
    public string? Category { get; set; }
}
