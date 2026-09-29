using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Admin product list: filters plus page/offset pagination (page number from 0, page size 1-50), so the dashboard can show totals and jump to any page.
/// </summary>
public class AdminProductFilter : BaseFilter
{
    /// <summary>Products whose name contains this text (case-insensitive).</summary>
    public string? Name { get; set; }

    /// <summary>furniture, electronics, beauty or garden (any letter case).</summary>
    public string? Category { get; set; }
}
