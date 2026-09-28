namespace AlQaseh_Ecommerce_API.Features.Auth.Dtos;

/// <summary>
/// Login credentials.
/// </summary>
public class LoginRequest
{
    /// <example>admin</example>
    public string UserName { get; set; } = string.Empty;

    /// <example>Admin123!</example>
    public string Password { get; set; } = string.Empty;
}
