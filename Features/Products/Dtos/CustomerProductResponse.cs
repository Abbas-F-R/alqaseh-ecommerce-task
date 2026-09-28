using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Product as seen by customers: no cost and no exact quantity, only a stock status.
/// </summary>
public class CustomerProductResponse
{
    [Sqid]
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Category { get; set; } = string.Empty;
    public decimal Price { get; set; }

    /// <summary>low (0-4), limited (5-9) or available (10 or more).</summary>
    public string StockStatus { get; set; } = StockStatuses.Available;
}

/// <summary>
/// Stock status shown to customers instead of the exact quantity.
/// </summary>
public static class StockStatuses
{
    public const string Low = "low";
    public const string Limited = "limited";
    public const string Available = "available";

    public static string FromQuantity(int quantity) => quantity switch
    {
        < 5 => Low,
        < 10 => Limited,
        _ => Available
    };
}
