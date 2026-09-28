namespace AlQaseh_Ecommerce_API.Features.Products;

/// <summary>
/// The four fixed product categories. They are stored and returned in lowercase; input is accepted in any letter case.
/// </summary>
public static class ProductCategories
{
    public const string Furniture = "furniture";
    public const string Electronics = "electronics";
    public const string Beauty = "beauty";
    public const string Garden = "garden";

    public static readonly IReadOnlyList<string> All = [Furniture, Electronics, Beauty, Garden];

    /// <summary>The canonical lowercase category, or null when the value is not one of the four.</summary>
    public static string? Normalize(string? value)
    {
        var candidate = value?.Trim().ToLowerInvariant();
        return candidate is not null && All.Contains(candidate) ? candidate : null;
    }
}
