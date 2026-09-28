namespace AlQaseh_Ecommerce_API.Shared.Attributes;

/// <summary>
/// Marks a numeric primary key or foreign key property for automatic Sqids URL-safe encoding and decoding.
/// </summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public class SqidAttribute : Attribute
{
}
