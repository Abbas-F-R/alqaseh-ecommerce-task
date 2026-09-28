namespace AlQaseh_Ecommerce_API.Shared.Constants;

/// <summary>
/// Short JWT claim names used in the access token (the bearer handler is configured not to remap them).
/// </summary>
public static class ClaimNames
{
    public const string UserId = "sub";
    public const string UserName = "name";
    public const string Role = "role";
}
