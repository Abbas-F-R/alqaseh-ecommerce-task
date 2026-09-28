namespace AlQaseh_Ecommerce_API.Shared.Attributes;

/// <summary>
/// Registers the decorated class with Scoped lifetime via Scrutor assembly scanning.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public class ScopedAttribute : Attribute
{
}

/// <summary>
/// Registers the decorated class with Transient lifetime via Scrutor assembly scanning.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public class TransientAttribute : Attribute
{
}

/// <summary>
/// Registers the decorated class with Singleton lifetime via Scrutor assembly scanning.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public class SingletonAttribute : Attribute
{
}
