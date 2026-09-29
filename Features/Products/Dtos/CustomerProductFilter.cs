namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Customer product list: filters plus cursor (keyset) pagination, made for browsing a large catalog page after page.
/// </summary>
public class CustomerProductFilter
{
    /// <summary>Maximum number of products to return (1-50, default 10).</summary>
    public int Limit { get; set; } = 10;

    /// <summary>Opaque continuation cursor: the <c>nextCursor</c> of the previous response.</summary>
    public string? Cursor { get; set; }

    /// <summary>Products whose name contains this text (case-insensitive).</summary>
    public string? Name { get; set; }

    /// <summary>furniture, electronics, beauty or garden (any letter case).</summary>
    public string? Category { get; set; }
}
