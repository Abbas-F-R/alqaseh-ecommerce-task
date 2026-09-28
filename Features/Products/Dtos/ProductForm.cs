namespace AlQaseh_Ecommerce_API.Features.Products.Dtos;

/// <summary>
/// Editable product fields, used to create a product and to replace an existing one.
/// </summary>
public class ProductForm
{
    /// <summary>Unique, case-insensitive.</summary>
    /// <example>Ergonomic Office Chair</example>
    public string Name { get; set; } = string.Empty;

    /// <summary>One of furniture, electronics, beauty, garden (any letter case).</summary>
    /// <example>furniture</example>
    public string Category { get; set; } = string.Empty;

    /// <summary>Selling price in IQD; greater than 0.</summary>
    /// <example>75000</example>
    public decimal Price { get; set; }

    /// <summary>Purchase cost in IQD; 0 or more.</summary>
    /// <example>50000</example>
    public decimal Cost { get; set; }

    /// <example>12</example>
    public int AvailableQuantity { get; set; }
}
